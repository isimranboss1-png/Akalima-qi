package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.config.CharacterProfile
import com.example.state.VisualState
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RoyalGold
import kotlinx.coroutines.delay

/**
 * Renders Aklima AI's real looping character videos on two stable TextureView layers
 * with a smooth 250ms crossfade and zero video player controls or black flashes.
 */
@Composable
fun CharacterVideo(
    character: CharacterProfile,
    visualState: VisualState,
    animationEnabled: Boolean,
    reducedMotion: Boolean,
    onVideoReadyChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val showSpeakingLayer = visualState == VisualState.SPEAKING
    val speakingAlpha by animateFloatAsState(
        targetValue = if (showSpeakingLayer) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 120 else 250),
        label = "speaking_crossfade"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "aura_pulse")
    val thinkingGlow by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_glow"
    )

    var idleReady by remember { mutableStateOf(false) }
    var speakingReady by remember { mutableStateOf(false) }

    LaunchedEffect(idleReady, speakingReady) {
        onVideoReadyChanged(idleReady && speakingReady)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // Subtle ambient atmospheric glow behind character
        Canvas(modifier = Modifier.fillMaxSize()) {
            val auraColor = when (visualState) {
                VisualState.SPEAKING -> RoyalGold.copy(alpha = if (reducedMotion) 0.22f else 0.30f)
                VisualState.THINKING -> CyanPulse.copy(alpha = if (reducedMotion) 0.24f else thinkingGlow)
                VisualState.IDLE -> CyanPulse.copy(alpha = 0.14f)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraColor, Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.42f),
                    radius = maxOf(size.width, size.height) * 0.58f
                )
            )
        }

        // Layer 1: Stable Idle video (also used smoothly for listening/thinking/error)
        VideoTextureLayer(
            videoResId = character.idleVideoResId,
            shouldPlay = animationEnabled && (!showSpeakingLayer || speakingAlpha < 0.99f),
            layerAlpha = 1f,
            onReadyChanged = { idleReady = it }
        )

        // Layer 2: Stable Speaking video crossfaded cleanly over Idle
        VideoTextureLayer(
            videoResId = character.speakingVideoResId,
            shouldPlay = animationEnabled && (showSpeakingLayer || speakingAlpha > 0.01f),
            layerAlpha = speakingAlpha,
            onReadyChanged = { speakingReady = it }
        )

        // Top & bottom radial/linear atmospheric scrims so UI overlays blend effortlessly
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to ObsidianVoid.copy(alpha = 0.55f),
                        0.18f to Color.Transparent,
                        0.68f to Color.Transparent,
                        0.88f to ObsidianVoid.copy(alpha = 0.72f),
                        1.0f to ObsidianVoid.copy(alpha = 0.94f)
                    )
                )
        )
    }
}

@Composable
private fun VideoTextureLayer(
    @RawRes videoResId: Int,
    shouldPlay: Boolean,
    layerAlpha: Float,
    onReadyChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var mediaPlayer by remember(videoResId) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(videoResId) { mutableStateOf(false) }
    var videoWidth by remember(videoResId) { mutableIntStateOf(0) }
    var videoHeight by remember(videoResId) { mutableIntStateOf(0) }
    var viewRef by remember(videoResId) { mutableStateOf<TextureView?>(null) }

    fun applyCenterCrop(tv: TextureView, vw: Int, vh: Int) {
        if (vw <= 0 || vh <= 0 || tv.width <= 0 || tv.height <= 0) return
        val viewW = tv.width.toFloat()
        val viewH = tv.height.toFloat()
        val scale = maxOf(viewW / vw.toFloat(), viewH / vh.toFloat())
        val scaledW = vw * scale
        val scaledH = vh * scale
        val dx = (viewW - scaledW) / 2f
        val dy = (viewH - scaledH) / 2f
        val matrix = Matrix().apply {
            setScale(scaledW / viewW, scaledH / viewH)
            postTranslate(dx, dy)
        }
        tv.setTransform(matrix)
    }

    LaunchedEffect(shouldPlay, prepared, layerAlpha) {
        val player = mediaPlayer ?: return@LaunchedEffect
        if (!prepared) return@LaunchedEffect
        runCatching {
            if (shouldPlay) {
                if (!player.isPlaying) {
                    player.start()
                }
            } else {
                // Pause only after fade-out completes to avoid frozen frame during crossfade
                if (layerAlpha <= 0.01f && player.isPlaying) {
                    delay(40)
                    player.pause()
                }
            }
        }
    }

    DisposableEffect(videoResId) {
        onDispose {
            onReadyChanged(false)
            prepared = false
            mediaPlayer?.let { mp ->
                runCatching {
                    if (mp.isPlaying) mp.stop()
                }
                runCatching { mp.release() }
            }
            mediaPlayer = null
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                val textureView = this
                isOpaque = false
                viewRef = textureView
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        val surface = Surface(surfaceTexture)
                        val uri = Uri.parse("android.resource://${context.packageName}/$videoResId")
                        val player = MediaPlayer().apply {
                            setDataSource(context, uri)
                            setSurface(surface)
                            isLooping = true
                            setVolume(0f, 0f)
                            setOnVideoSizeChangedListener { _, vw, vh ->
                                videoWidth = vw
                                videoHeight = vh
                                applyCenterCrop(textureView, vw, vh)
                            }
                            setOnPreparedListener { mp ->
                                prepared = true
                                onReadyChanged(true)
                                applyCenterCrop(textureView, videoWidth, videoHeight)
                                if (shouldPlay) {
                                    runCatching { mp.start() }
                                } else {
                                    // Seek to frame 0 and keep paused until active
                                    runCatching {
                                        mp.seekTo(0)
                                    }
                                }
                            }
                            setOnErrorListener { _, _, _ ->
                                onReadyChanged(false)
                                true
                            }
                            prepareAsync()
                        }
                        mediaPlayer = player
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        applyCenterCrop(textureView, videoWidth, videoHeight)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        prepared = false
                        onReadyChanged(false)
                        mediaPlayer?.let { mp ->
                            runCatching {
                                if (mp.isPlaying) mp.stop()
                                mp.release()
                            }
                        }
                        mediaPlayer = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
        update = { tv ->
            viewRef = tv
            if (videoWidth > 0 && videoHeight > 0) {
                applyCenterCrop(tv, videoWidth, videoHeight)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = layerAlpha }
    )
}
