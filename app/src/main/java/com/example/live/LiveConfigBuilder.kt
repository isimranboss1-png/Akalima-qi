package com.example.live

import android.util.Log
import com.example.config.AKLIMA_SYSTEM_INSTRUCTION
import com.example.config.AKLIMA_VOICE_NAME
import com.example.config.AppConstants
import com.example.config.CharacterProfile
import com.example.config.CharacterRegistry
import com.example.tools.ToolManager
import com.google.firebase.Firebase
import com.google.firebase.ai.LiveGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.ContextWindowCompressionConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SlidingWindow
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig

private const val TAG = "LiveConfigBuilder"

/**
 * Single centralized config builder for every Gemini Live connection and reconnect.
 * Guarantees Aklima AI always uses the locked female voice (Aoede).
 */
object LiveConfigBuilder {

    @OptIn(PublicPreviewAPI::class)
    fun buildLiveModel(character: CharacterProfile = CharacterRegistry.aklima): LiveGenerativeModel {
        // Enforce female voice invariant from single source of truth
        val voiceName = character.voiceName.ifBlank { AKLIMA_VOICE_NAME }
        Log.d(TAG, "Building LiveGenerativeModel: model=${AppConstants.LIVE_MODEL_NAME}, voiceName=$voiceName")

        val generationConfig = liveGenerationConfig {
            responseModality = ResponseModality.AUDIO
            speechConfig = SpeechConfig(voice = Voice(voiceName))
            inputAudioTranscription = AudioTranscriptionConfig()
            outputAudioTranscription = AudioTranscriptionConfig()
            contextWindowCompression = ContextWindowCompressionConfig(
                slidingWindow = SlidingWindow()
            )
        }

        val systemInstruction = content {
            text(AKLIMA_SYSTEM_INSTRUCTION)
        }

        return Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = AppConstants.LIVE_MODEL_NAME,
            generationConfig = generationConfig,
            tools = listOf(ToolManager.toolDefinition),
            systemInstruction = systemInstruction
        )
    }
}
