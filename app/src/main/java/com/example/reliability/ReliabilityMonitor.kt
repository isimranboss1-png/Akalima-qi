package com.example.reliability

import android.util.Log
import com.example.config.AKLIMA_VOICE_NAME
import com.example.config.AppConstants
import com.example.security.isAppCheckConfigured
import com.example.state.AssistantStore
import com.example.state.ConnectionState
import com.example.state.ScreenShareState
import com.example.state.ScreenShareStore
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "ReliabilityMonitor"

enum class HealthSeverity {
    OK,
    WARNING,
    CRITICAL
}

enum class AuditEventType {
    HEALTH_CHECK,
    DEEP_INSPECTION,
    INCIDENT,
    AUTO_REPAIR,
    ROLLBACK,
    ESCALATION,
    TOOL_ACTION
}

data class AuditLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val type: AuditEventType,
    val title: String,
    val detail: String,
    val severity: HealthSeverity
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }
}

data class SubsystemStatus(
    val name: String,
    val healthy: Boolean,
    val detail: String
)

data class ReliabilitySnapshot(
    val overallSeverity: HealthSeverity = HealthSeverity.OK,
    val lastCheckAtMs: Long = 0L,
    val nextCheckAtMs: Long = 0L,
    val lastDeepInspectionAtMs: Long = 0L,
    val subsystems: List<SubsystemStatus> = emptyList(),
    val incidentsCount: Int = 0,
    val repairsCount: Int = 0,
    val failuresCount: Int = 0,
    val rollbacksCount: Int = 0,
    val latestDiagnosis: String? = null,
    val escalationNotice: String? = null,
    val auditLog: List<AuditLogEntry> = emptyList()
)

class ReliabilityMonitor(
    private val assistantStore: AssistantStore,
    private val screenShareStore: ScreenShareStore
) {
    private val _snapshot = MutableStateFlow(ReliabilitySnapshot())
    val snapshot: StateFlow<ReliabilitySnapshot> = _snapshot.asStateFlow()

    private val diagnosticModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-3.8-flash")
    }

    private var monitorJob: Job? = null
    private var deepJob: Job? = null

    fun startMonitoring(
        scope: CoroutineScope,
        isAudioOutputHealthy: () -> Boolean,
        recoverAudioOutput: () -> Unit,
        isVideoReady: () -> Boolean,
        onReconnectRequested: () -> Unit
    ) {
        if (monitorJob?.isActive == true) return

        monitorJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                runHealthCheck(
                    isAudioOutputHealthy = isAudioOutputHealthy,
                    recoverAudioOutput = recoverAudioOutput,
                    isVideoReady = isVideoReady,
                    onReconnectRequested = onReconnectRequested,
                    isDeep = false
                )
                delay(AppConstants.HEALTH_CHECK_INTERVAL_MS)
            }
        }

        deepJob = scope.launch(Dispatchers.Default) {
            delay(15_000L)
            while (isActive) {
                runHealthCheck(
                    isAudioOutputHealthy = isAudioOutputHealthy,
                    recoverAudioOutput = recoverAudioOutput,
                    isVideoReady = isVideoReady,
                    onReconnectRequested = onReconnectRequested,
                    isDeep = true
                )
                delay(AppConstants.DEEP_INSPECTION_INTERVAL_MS)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        deepJob?.cancel()
        deepJob = null
    }

    fun runManualCheck(
        scope: CoroutineScope,
        isAudioOutputHealthy: () -> Boolean,
        recoverAudioOutput: () -> Unit,
        isVideoReady: () -> Boolean,
        onReconnectRequested: () -> Unit
    ) {
        scope.launch(Dispatchers.Default) {
            runHealthCheck(
                isAudioOutputHealthy = isAudioOutputHealthy,
                recoverAudioOutput = recoverAudioOutput,
                isVideoReady = isVideoReady,
                onReconnectRequested = onReconnectRequested,
                isDeep = true
            )
        }
    }

    private suspend fun runHealthCheck(
        isAudioOutputHealthy: () -> Boolean,
        recoverAudioOutput: () -> Unit,
        isVideoReady: () -> Boolean,
        onReconnectRequested: () -> Unit,
        isDeep: Boolean
    ) {
        val now = System.currentTimeMillis()
        val assistant = assistantStore.state.value
        val screenShare = screenShareStore.state.value

        val appCheckOk = isAppCheckConfigured
        val voiceLockedOk = assistant.activeVoiceName == AKLIMA_VOICE_NAME
        val audioOutOk = isAudioOutputHealthy()
        val videoOk = isVideoReady()
        val connectionOk = assistant.connectionState != ConnectionState.DISCONNECTED || assistant.errorMessage == null
        val screenShareOk = screenShare.state != ScreenShareState.ERROR

        // Safe reversible self-healing
        if (!voiceLockedOk) {
            val prevVoice = assistant.activeVoiceName
            assistantStore.setActiveVoiceName(AKLIMA_VOICE_NAME)
            recordAudit(
                AuditEventType.AUTO_REPAIR,
                "Voice Lock Restored",
                "Restored female voice from '$prevVoice' to '$AKLIMA_VOICE_NAME'.",
                HealthSeverity.WARNING,
                isRepair = true
            )
        }

        if (!audioOutOk) {
            recordAudit(
                AuditEventType.INCIDENT,
                "AudioTrack Uninitialized",
                "24 kHz playback buffer unhealthy; attempting checkpoint recovery.",
                HealthSeverity.WARNING,
                isIncident = true
            )
            runCatching { recoverAudioOutput() }
            if (isAudioOutputHealthy()) {
                recordAudit(
                    AuditEventType.AUTO_REPAIR,
                    "AudioTrack Recovered",
                    "Re-initialized 24 kHz PCM16 playback stream cleanly.",
                    HealthSeverity.OK,
                    isRepair = true
                )
            } else {
                recordAudit(
                    AuditEventType.ROLLBACK,
                    "AudioTrack Recovery Failed",
                    "Rolled back playback trigger; audio hardware needs attention.",
                    HealthSeverity.CRITICAL,
                    isFailure = true,
                    isRollback = true
                )
            }
        }

        if (assistant.errorMessage != null && assistant.connectionState == ConnectionState.DISCONNECTED) {
            recordAudit(
                AuditEventType.INCIDENT,
                "Live Session Disconnected",
                assistant.errorMessage,
                HealthSeverity.WARNING,
                isIncident = true
            )
            if (isDeep) {
                diagnoseIncidentWithGemini(
                    "Connection dropped (${assistant.errorMessage}); AppCheck=$appCheckOk; Voice=${assistant.activeVoiceName}"
                )
                onReconnectRequested()
            }
        }

        val statuses = listOf(
            SubsystemStatus("Firebase App Check", appCheckOk, if (appCheckOk) "Attestation ready" else "Not initialized"),
            SubsystemStatus("Female Voice Lock", voiceLockedOk, "Locked: $AKLIMA_VOICE_NAME"),
            SubsystemStatus(
                "Gemini Live Session",
                connectionOk,
                "${assistant.connectionState.name} (${assistant.assistantState.name})"
            ),
            SubsystemStatus("24 kHz Audio Output", isAudioOutputHealthy(), if (isAudioOutputHealthy()) "Stream ready" else "Standby / Recovering"),
            SubsystemStatus("Character Video Engine", videoOk, if (videoOk) "Surfaces active" else "Preparing surfaces"),
            SubsystemStatus("Screen Capture", screenShareOk, screenShare.state.name)
        )

        val overall = when {
            !appCheckOk -> HealthSeverity.CRITICAL
            !connectionOk || !screenShareOk -> HealthSeverity.WARNING
            else -> HealthSeverity.OK
        }

        _snapshot.update {
            it.copy(
                overallSeverity = overall,
                lastCheckAtMs = now,
                nextCheckAtMs = now + AppConstants.HEALTH_CHECK_INTERVAL_MS,
                lastDeepInspectionAtMs = if (isDeep) now else it.lastDeepInspectionAtMs,
                subsystems = statuses
            )
        }

        recordAudit(
            type = if (isDeep) AuditEventType.DEEP_INSPECTION else AuditEventType.HEALTH_CHECK,
            title = if (isDeep) "Deep Inspection Complete" else "Periodic Health Check",
            detail = "Status: ${overall.name} (${statuses.count { it.healthy }}/${statuses.size} healthy)",
            severity = overall
        )
    }

    private suspend fun diagnoseIncidentWithGemini(summary: String) {
        try {
            val diagnosis = withTimeout(15_000L) {
                diagnosticModel.generateContent(
                    "You are a concise Android DevOps reliability assistant. In 1-2 short plain sentences, identify the likely cause and safest reversible recovery step for this runtime telemetry (never suggest disabling security or exposing secrets): $summary"
                ).text?.trim()
            }
            if (!diagnosis.isNullOrBlank()) {
                _snapshot.update { it.copy(latestDiagnosis = diagnosis) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini diagnostic skipped: ${e.localizedMessage}")
        }
    }

    fun recordAudit(
        type: AuditEventType,
        title: String,
        detail: String,
        severity: HealthSeverity,
        isIncident: Boolean = false,
        isRepair: Boolean = false,
        isFailure: Boolean = false,
        isRollback: Boolean = false,
        escalationText: String? = null
    ) {
        val entry = AuditLogEntry(
            type = type,
            title = title,
            detail = detail,
            severity = severity
        )
        _snapshot.update { current ->
            val updatedLog = (listOf(entry) + current.auditLog).take(AppConstants.MAX_AUDIT_LOG_ENTRIES)
            current.copy(
                incidentsCount = current.incidentsCount + if (isIncident) 1 else 0,
                repairsCount = current.repairsCount + if (isRepair) 1 else 0,
                failuresCount = current.failuresCount + if (isFailure) 1 else 0,
                rollbacksCount = current.rollbacksCount + if (isRollback) 1 else 0,
                escalationNotice = escalationText ?: current.escalationNotice,
                auditLog = updatedLog
            )
        }
    }

    fun dismissEscalation() {
        _snapshot.update { it.copy(escalationNotice = null) }
    }
}
