package com.example.screenshare

import android.graphics.Bitmap
import android.graphics.Color
import com.example.config.AppConstants
import kotlin.math.abs

/**
 * Downsamples frames to 64x36 grayscale in memory and compares against the last
 * sent frame to avoid sending duplicate/unchanged screen frames.
 */
class ScreenChangeDetector {
    private val sampleW = 64
    private val sampleH = 36
    private var lastSentFingerprint: IntArray? = null

    @Synchronized
    fun shouldSendFrame(
        bitmap: Bitmap,
        nowMs: Long,
        lastSentAtMs: Long,
        forceSend: Boolean
    ): Boolean {
        val current = extractGrayscaleFingerprint(bitmap)
        val previous = lastSentFingerprint

        if (previous == null || forceSend) {
            lastSentFingerprint = current
            return true
        }

        if (nowMs - lastSentAtMs >= AppConstants.SCREEN_FRAME_STALE_MS) {
            lastSentFingerprint = current
            return true
        }

        var diffSum = 0L
        for (i in current.indices) {
            diffSum += abs(current[i] - previous[i])
        }
        val normalizedMeanDiff = diffSum.toFloat() / (current.size * 255f)
        return if (normalizedMeanDiff >= AppConstants.SCREEN_CHANGE_THRESHOLD) {
            lastSentFingerprint = current
            true
        } else {
            false
        }
    }

    @Synchronized
    fun reset() {
        lastSentFingerprint = null
    }

    private fun extractGrayscaleFingerprint(source: Bitmap): IntArray {
        val scaled = Bitmap.createScaledBitmap(source, sampleW, sampleH, false)
        val pixels = IntArray(sampleW * sampleH)
        scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        if (scaled !== source) {
            scaled.recycle()
        }
        val gray = IntArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            gray[i] = (r * 30 + g * 59 + b * 11) / 100
        }
        return gray
    }
}
