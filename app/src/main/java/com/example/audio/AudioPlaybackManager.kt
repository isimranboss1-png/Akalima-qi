package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.config.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

private const val TAG = "AudioPlaybackManager"

/**
 * Plays Gemini Live 24 kHz 16-bit PCM mono audio seamlessly with gapless queueing.
 * Sample rate is locked to 24000 Hz with zero pitch or playbackRate adjustments.
 */
class AudioPlaybackManager(
    private val onSpeakingStateChanged: (Boolean) -> Unit,
    private val onAmplitude: (Float) -> Unit
) {
    private val queue = ConcurrentLinkedQueue<ByteArray>()
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    @Volatile
    private var isMuted: Boolean = false

    @Volatile
    private var isActivePlayback: Boolean = false

    @Volatile
    private var turnCompleteReceived: Boolean = false

    @Volatile
    private var interruptedFlag: Boolean = false

    @Volatile
    private var totalFramesWritten: Long = 0L

    fun start(scope: CoroutineScope) {
        if (playbackJob?.isActive == true) return
        ensureAudioTrack()

        playbackJob = scope.launch(Dispatchers.IO) {
            var lastWriteTimeMs = 0L
            while (isActive) {
                if (interruptedFlag) {
                    delay(20)
                    continue
                }

                val chunk = queue.poll()
                if (chunk != null && chunk.isNotEmpty()) {
                    val track = ensureAudioTrack()
                    if (track != null) {
                        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            runCatching { track.play() }
                        }
                        if (!isActivePlayback) {
                            isActivePlayback = true
                            onSpeakingStateChanged(true)
                        }
                        onAmplitude(computeRmsNormalized(chunk))
                        val written = track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                        if (written > 0) {
                            totalFramesWritten += (written / 2)
                            lastWriteTimeMs = System.currentTimeMillis()
                        }
                    }
                } else {
                    if (isActivePlayback) {
                        val track = audioTrack
                        val headPos = track?.playbackHeadPosition?.toLong() ?: totalFramesWritten
                        val elapsedSinceWrite = System.currentTimeMillis() - lastWriteTimeMs
                        val drained = (turnCompleteReceived && headPos >= totalFramesWritten - 240) ||
                            (turnCompleteReceived && elapsedSinceWrite > 280L) ||
                            elapsedSinceWrite > 900L
                        if (drained) {
                            isActivePlayback = false
                            turnCompleteReceived = false
                            onAmplitude(0f)
                            onSpeakingStateChanged(false)
                        } else {
                            onAmplitude(0.06f)
                        }
                    }
                    delay(16)
                }
            }
        }
    }

    fun enqueue(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return
        interruptedFlag = false
        turnCompleteReceived = false
        queue.offer(pcmBytes)
    }

    fun markTurnComplete() {
        turnCompleteReceived = true
    }

    fun interrupt() {
        interruptedFlag = true
        turnCompleteReceived = false
        queue.clear()
        audioTrack?.let { track ->
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
                track.flush()
            }
        }
        totalFramesWritten = audioTrack?.playbackHeadPosition?.toLong() ?: 0L
        if (isActivePlayback) {
            isActivePlayback = false
            onAmplitude(0f)
            onSpeakingStateChanged(false)
        } else {
            onAmplitude(0f)
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        audioTrack?.let { track ->
            runCatching { track.setVolume(if (muted) 0f else 1f) }
        }
    }

    fun isHealthy(): Boolean {
        return audioTrack?.state == AudioTrack.STATE_INITIALIZED
    }

    fun recover() {
        Log.w(TAG, "Recovering AudioTrack pipeline")
        releaseTrackOnly()
        ensureAudioTrack()
    }

    fun release() {
        playbackJob?.cancel()
        playbackJob = null
        queue.clear()
        isActivePlayback = false
        releaseTrackOnly()
    }

    @Synchronized
    private fun ensureAudioTrack(): AudioTrack? {
        val existing = audioTrack
        if (existing != null && existing.state == AudioTrack.STATE_INITIALIZED) {
            return existing
        }
        releaseTrackOnly()

        val sampleRate = AppConstants.OUTPUT_SAMPLE_RATE_HZ // 24000 Hz locked
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            Log.e(TAG, "Invalid AudioTrack minBufferSize=$minBuf")
            return null
        }

        return try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf * 3, 16_384))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                null
            } else {
                track.setVolume(if (isMuted) 0f else 1f)
                track.play()
                totalFramesWritten = 0L
                audioTrack = track
                track
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack at 24000 Hz", e)
            null
        }
    }

    private fun releaseTrackOnly() {
        audioTrack?.let { track ->
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
            }
            runCatching { track.release() }
        }
        audioTrack = null
        totalFramesWritten = 0L
    }

    private fun computeRmsNormalized(pcm16Le: ByteArray): Float {
        val samples = pcm16Le.size / 2
        if (samples <= 0) return 0f
        var sumSq = 0.0
        var i = 0
        while (i + 1 < pcm16Le.size) {
            val lo = pcm16Le[i].toInt() and 0xFF
            val hi = pcm16Le[i + 1].toInt()
            val sample = (hi shl 8) or lo
            val norm = sample / 32768.0
            sumSq += norm * norm
            i += 2
        }
        val rms = sqrt(sumSq / samples).toFloat()
        return (rms * 3.5f).coerceIn(0f, 1f)
    }
}
