package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.example.config.AppConstants
import com.example.state.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private const val TAG = "AudioCaptureManager"

class AudioCaptureManager {
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var captureJob: Job? = null

    @Volatile
    var isCapturing: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onChunk: suspend (ByteArray) -> Unit,
        onAmplitude: (Float) -> Unit,
        onSpeechActivity: () -> Unit,
        onError: (AppError) -> Unit
    ): Boolean {
        if (isCapturing) return true

        val sampleRate = AppConstants.INPUT_SAMPLE_RATE_HZ
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT

        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        if (minBuf <= 0) {
            onError(AppError.AudioContextError("Unsupported 16 kHz microphone format"))
            return false
        }

        // ~32ms frames = 512 samples * 2 bytes = 1024 bytes
        val frameBytes = 1024
        val bufferSize = maxOf(minBuf * 2, frameBytes * 4)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                encoding,
                bufferSize
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission missing", e)
            onError(AppError.MicrophonePermissionDenied())
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            onError(AppError.AudioContextError("Microphone unavailable"))
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onError(AppError.AudioContextError("AudioRecord initialization failed"))
            return false
        }

        runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply {
                    enabled = true
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply {
                    enabled = true
                }
            }
        }

        audioRecord = record
        return try {
            record.startRecording()
            isCapturing = true

            captureJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(frameBytes)
                var speechCooldownMs = 0L
                while (isActive && isCapturing) {
                    val bytesRead = record.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        val chunk = buffer.copyOf(bytesRead)
                        val rms = computeRmsNormalized(chunk, bytesRead)
                        onAmplitude(rms)
                        if (rms > 0.14f) {
                            val now = System.currentTimeMillis()
                            if (now - speechCooldownMs > 600L) {
                                speechCooldownMs = now
                                onSpeechActivity()
                            }
                        }
                        onChunk(chunk)
                    } else if (bytesRead < 0) {
                        Log.w(TAG, "AudioRecord read error code: $bytesRead")
                        break
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            stop()
            onError(AppError.AudioContextError("Could not start microphone stream"))
            false
        }
    }

    fun stop() {
        isCapturing = false
        captureJob?.cancel()
        captureJob = null

        runCatching { echoCanceler?.release() }
        echoCanceler = null
        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null

        audioRecord?.let { record ->
            runCatching {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            }
            runCatching { record.release() }
        }
        audioRecord = null
    }

    private fun computeRmsNormalized(pcm16Le: ByteArray, length: Int): Float {
        val samples = length / 2
        if (samples <= 0) return 0f
        var sumSq = 0.0
        var i = 0
        while (i + 1 < length) {
            val lo = pcm16Le[i].toInt() and 0xFF
            val hi = pcm16Le[i + 1].toInt()
            val sample = (hi shl 8) or lo
            val norm = sample / 32768.0
            sumSq += norm * norm
            i += 2
        }
        val rms = sqrt(sumSq / samples).toFloat()
        return (rms * 3.2f).coerceIn(0f, 1f)
    }
}
