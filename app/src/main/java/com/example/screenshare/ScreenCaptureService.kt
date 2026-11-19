package com.example.screenshare

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "ScreenCaptureService"
private const val CHANNEL_ID = "agents_ai_screen_share"
private const val NOTIFICATION_ID = 4102

object ScreenCaptureBridge {
    @Volatile
    var onBitmapCaptured: ((Bitmap) -> Unit)? = null

    @Volatile
    var onCaptureStarted: (() -> Unit)? = null

    @Volatile
    var onCaptureStopped: ((String?) -> Unit)? = null

    @Volatile
    var onCaptureError: ((String) -> Unit)? = null
}

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.example.screenshare.ACTION_START"
        const val ACTION_STOP = "com.example.screenshare.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun createStartIntent(context: Context, resultCode: Int, data: Intent): Intent {
            return Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var captureWidth: Int = 720
    private var captureHeight: Int = 1280
    private var captureDpi: Int = 320

    private val isProcessingFrame = AtomicBoolean(false)
    private val isStopping = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopProjectionAndSelf("Screen sharing stopped.")
                return START_NOT_STICKY
            }

            ACTION_START -> {
                isStopping.set(false)
                // Must enter foreground with mediaProjection type BEFORE calling getMediaProjection
                startForegroundWithNotification()

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != Activity.RESULT_OK || data == null) {
                    ScreenCaptureBridge.onCaptureError?.invoke("Screen sharing permission was not granted.")
                    stopProjectionAndSelf(null)
                    return START_NOT_STICKY
                }

                startProjection(resultCode, data)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val projection = try {
            projectionManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "getMediaProjection failed", e)
            ScreenCaptureBridge.onCaptureError?.invoke("Could not start screen sharing.")
            stopProjectionAndSelf(null)
            return
        }

        if (projection == null) {
            ScreenCaptureBridge.onCaptureError?.invoke("Screen sharing permission was cancelled.")
            stopProjectionAndSelf(null)
            return
        }

        initCaptureThread()
        computeDisplayMetrics()

        // Register callback BEFORE createVirtualDisplay as required by Android 14+
        projection.registerCallback(projectionCallback, captureHandler)
        mediaProjection = projection

        setupVirtualDisplay(projection, captureWidth, captureHeight)
        ScreenCaptureBridge.onCaptureStarted?.invoke()
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection onStop triggered by system or user")
            stopProjectionAndSelf("Screen sharing stopped.")
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width > 0 && height > 0 && (width != captureWidth || height != captureHeight)) {
                Log.d(TAG, "Captured content resized to ${width}x$height")
                captureWidth = width
                captureHeight = height
                recreateImageReaderAndSurface()
            }
        }
    }

    private fun setupVirtualDisplay(projection: MediaProjection, width: Int, height: Int) {
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ r ->
            if (isStopping.get()) return@setOnImageAvailableListener
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (!isProcessingFrame.compareAndSet(false, true)) {
                image.close()
                return@setOnImageAvailableListener
            }
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val bitmapWidth = image.width + rowPadding / pixelStride
                val rawBitmap = Bitmap.createBitmap(
                    bitmapWidth,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                rawBitmap.copyPixelsFromBuffer(buffer)
                val cropped = if (bitmapWidth != image.width) {
                    val c = Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height)
                    rawBitmap.recycle()
                    c
                } else {
                    rawBitmap
                }
                ScreenCaptureBridge.onBitmapCaptured?.invoke(cropped)
            } catch (e: Exception) {
                Log.w(TAG, "ImageReader frame extraction error: ${e.localizedMessage}")
            } finally {
                image.close()
                isProcessingFrame.set(false)
            }
        }, captureHandler)

        imageReader = reader
        virtualDisplay = projection.createVirtualDisplay(
            "AgentsAiScreenShare",
            width,
            height,
            captureDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            captureHandler
        )
    }

    private fun recreateImageReaderAndSurface() {
        val vd = virtualDisplay ?: return
        val oldReader = imageReader
        val newReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        newReader.setOnImageAvailableListener({ r ->
            if (isStopping.get()) return@setOnImageAvailableListener
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (!isProcessingFrame.compareAndSet(false, true)) {
                image.close()
                return@setOnImageAvailableListener
            }
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val bitmapWidth = image.width + rowPadding / pixelStride
                val rawBitmap = Bitmap.createBitmap(bitmapWidth, image.height, Bitmap.Config.ARGB_8888)
                rawBitmap.copyPixelsFromBuffer(buffer)
                val cropped = if (bitmapWidth != image.width) {
                    val c = Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height)
                    rawBitmap.recycle()
                    c
                } else {
                    rawBitmap
                }
                ScreenCaptureBridge.onBitmapCaptured?.invoke(cropped)
            } catch (_: Exception) {
            } finally {
                image.close()
                isProcessingFrame.set(false)
            }
        }, captureHandler)
        vd.resize(captureWidth, captureHeight, captureDpi)
        vd.surface = newReader.surface
        imageReader = newReader
        oldReader?.setOnImageAvailableListener(null, null)
        oldReader?.close()
    }

    private fun computeDisplayMetrics() {
        val wm = getSystemService(WindowManager::class.java)
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.maximumWindowMetrics.bounds
        } else {
            val dm = resources.displayMetrics
            android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
        val rawW = maxOf(1, bounds.width())
        val rawH = maxOf(1, bounds.height())
        // Capture at <= 1024px max dimension to save memory & CPU
        val scale = minOf(1f, 1024f / maxOf(rawW, rawH).toFloat())
        captureWidth = maxOf(2, (rawW * scale).toInt() and 0xFFFFFFFE.toInt())
        captureHeight = maxOf(2, (rawH * scale).toInt() and 0xFFFFFFFE.toInt())
        captureDpi = resources.displayMetrics.densityDpi
    }

    private fun initCaptureThread() {
        if (captureThread == null) {
            captureThread = HandlerThread("AgentsAiScreenCapture").apply { start() }
            captureHandler = Handler(captureThread!!.looper)
        }
    }

    private fun stopProjectionAndSelf(notice: String?) {
        if (!isStopping.compareAndSet(false, true)) return

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        }
        imageReader = null

        runCatching {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        }
        mediaProjection = null

        runCatching { captureThread?.quitSafely() }
        captureThread = null
        captureHandler = null

        ScreenCaptureBridge.onCaptureStopped?.invoke(notice)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopProjectionAndSelf(null)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_share_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.screen_share_notification_text)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            createStopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.screen_share_notification_title))
            .setContentText(getString(R.string.screen_share_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.screen_share_stop), stopIntent)
            .build()
    }
}
