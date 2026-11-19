package com.example.screenshare

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.example.live.LiveSessionManager
import com.example.reliability.AuditEventType
import com.example.reliability.HealthSeverity
import com.example.reliability.ReliabilityMonitor
import com.example.state.AssistantStore
import com.example.state.ConnectionState
import com.example.state.ScreenShareState
import com.example.state.ScreenShareStore
import com.example.state.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "ScreenShareManager"

class ScreenShareManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val screenShareStore: ScreenShareStore,
    private val assistantStore: AssistantStore,
    private val settingsStore: SettingsStore,
    private val liveSessionManager: LiveSessionManager,
    private val reliabilityMonitor: ReliabilityMonitor
) {
    private val changeDetector = ScreenChangeDetector()
    private val frameInFlight = AtomicBoolean(false)

    @Volatile
    private var lastAttemptAtMs: Long = 0L

    @Volatile
    private var lastSentAtMs: Long = 0L

    @Volatile
    private var forceNextFrame: Boolean = false

    @Volatile
    private var isPausedByLifecycle: Boolean = false

    @Volatile
    private var latestBitmap: Bitmap? = null

    init {
        ScreenCaptureBridge.onCaptureStarted = {
            changeDetector.reset()
            forceNextFrame = true
            screenShareStore.resetSessionStats()
            screenShareStore.setState(ScreenShareState.ACTIVE, "Screen sharing is active.")
            liveSessionManager.sendContextNote(
                "Screen sharing is now active. You will receive screen frames when the screen updates."
            )
            reliabilityMonitor.recordAudit(
                type = AuditEventType.HEALTH_CHECK,
                title = "Screen Share Started",
                detail = "MediaProjection foreground service active.",
                severity = HealthSeverity.OK
            )
        }

        ScreenCaptureBridge.onCaptureStopped = { notice ->
            clearBufferedFrame()
            changeDetector.reset()
            val wasActive = screenShareStore.state.value.state != ScreenShareState.OFF
            screenShareStore.setState(ScreenShareState.OFF, notice)
            if (wasActive) {
                liveSessionManager.sendContextNote(
                    "Screen sharing has stopped. Do not treat any earlier screen frames as current."
                )
            }
        }

        ScreenCaptureBridge.onCaptureError = { msg ->
            clearBufferedFrame()
            changeDetector.reset()
            screenShareStore.setError(msg)
            reliabilityMonitor.recordAudit(
                type = AuditEventType.INCIDENT,
                title = "Screen Share Error",
                detail = msg,
                severity = HealthSeverity.WARNING,
                isIncident = true
            )
        }

        ScreenCaptureBridge.onBitmapCaptured = { bitmap ->
            handleIncomingBitmap(bitmap)
        }
    }

    fun onPermissionGranted(resultCode: Int, data: Intent) {
        screenShareStore.setState(ScreenShareState.STARTING, null)
        val intent = ScreenCaptureService.createStartIntent(context, resultCode, data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun onPermissionDenied(cancelled: Boolean) {
        val msg = if (cancelled) {
            "Screen sharing permission was not granted."
        } else {
            "Screen sharing permission was denied."
        }
        screenShareStore.setError(msg)
    }

    fun stopSharing() {
        val current = screenShareStore.state.value.state
        if (current == ScreenShareState.OFF) return
        screenShareStore.setState(ScreenShareState.STOPPING, null)
        context.startService(ScreenCaptureService.createStopIntent(context))
    }

    fun requestForcedFrameSend() {
        forceNextFrame = true
        val bmp = synchronized(this) { latestBitmap }
        if (bmp != null && !bmp.isRecycled &&
            screenShareStore.state.value.state == ScreenShareState.ACTIVE &&
            !isPausedByLifecycle
        ) {
            processAndSendBitmap(bmp, force = true)
        }
    }

    fun onAppPaused() {
        isPausedByLifecycle = true
        if (screenShareStore.state.value.state == ScreenShareState.ACTIVE) {
            screenShareStore.setState(ScreenShareState.PAUSED, null)
        }
    }

    fun onAppResumed() {
        isPausedByLifecycle = false
        if (screenShareStore.state.value.state == ScreenShareState.PAUSED) {
            forceNextFrame = true
            screenShareStore.setState(ScreenShareState.ACTIVE, "Screen sharing is active.")
        }
    }

    private fun handleIncomingBitmap(bitmap: Bitmap) {
        synchronized(this) {
            val old = latestBitmap
            latestBitmap = bitmap
            if (old != null && old !== bitmap && !old.isRecycled) {
                old.recycle()
            }
        }

        val state = screenShareStore.state.value.state
        if (state != ScreenShareState.ACTIVE || isPausedByLifecycle) return
        if (assistantStore.state.value.connectionState != ConnectionState.CONNECTED) return

        val settings = settingsStore.settings.value
        val now = System.currentTimeMillis()
        val forced = forceNextFrame
        if (!forced && now - lastAttemptAtMs < settings.screenFrameRate.intervalMs) {
            return
        }
        lastAttemptAtMs = now
        processAndSendBitmap(bitmap, force = forced)
    }

    private fun processAndSendBitmap(bitmap: Bitmap, force: Boolean) {
        if (!frameInFlight.compareAndSet(false, true)) {
            return
        }
        val copy = try {
            if (bitmap.isRecycled) {
                frameInFlight.set(false)
                return
            }
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            frameInFlight.set(false)
            return
        }

        scope.launch(Dispatchers.Default) {
            try {
                val now = System.currentTimeMillis()
                val shouldSend = changeDetector.shouldSendFrame(
                    bitmap = copy,
                    nowMs = now,
                    lastSentAtMs = lastSentAtMs,
                    forceSend = force
                )
                if (shouldSend) {
                    val quality = settingsStore.settings.value.screenFrameQuality
                    val jpegBytes = ScreenFrameProcessor.encodeJpeg(copy, quality)
                    if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                        forceNextFrame = false
                        lastSentAtMs = now
                        liveSessionManager.sendScreenFrame(jpegBytes)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Frame processing skipped: ${e.localizedMessage}")
            } finally {
                if (!copy.isRecycled) copy.recycle()
                frameInFlight.set(false)
            }
        }
    }

    private fun clearBufferedFrame() {
        synchronized(this) {
            latestBitmap?.let { if (!it.isRecycled) it.recycle() }
            latestBitmap = null
        }
    }

    fun release() {
        ScreenCaptureBridge.onCaptureStarted = null
        ScreenCaptureBridge.onCaptureStopped = null
        ScreenCaptureBridge.onCaptureError = null
        ScreenCaptureBridge.onBitmapCaptured = null
        clearBufferedFrame()
    }
}
