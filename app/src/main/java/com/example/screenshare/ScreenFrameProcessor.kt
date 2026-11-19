package com.example.screenshare

import android.graphics.Bitmap
import com.example.state.FrameQuality
import java.io.ByteArrayOutputStream

/**
 * Resizes captured screen bitmaps without upscaling and encodes JPEG purely in memory.
 */
object ScreenFrameProcessor {
    fun encodeJpeg(source: Bitmap, quality: FrameQuality): ByteArray? {
        val maxDim = quality.maxDim
        val srcW = source.width
        val srcH = source.height
        if (srcW <= 0 || srcH <= 0) return null

        val scale = minOf(1f, maxDim.toFloat() / maxOf(srcW, srcH).toFloat())
        val targetW = maxOf(1, (srcW * scale).toInt())
        val targetH = maxOf(1, (srcH * scale).toInt())

        val scaled = if (targetW != srcW || targetH != srcH) {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        } else {
            source
        }

        return try {
            val out = ByteArrayOutputStream(32 * 1024)
            scaled.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, out)
            out.toByteArray()
        } finally {
            if (scaled !== source) {
                scaled.recycle()
            }
        }
    }
}
