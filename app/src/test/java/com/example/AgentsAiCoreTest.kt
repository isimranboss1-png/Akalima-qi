package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.config.AKLIMA_SYSTEM_INSTRUCTION
import com.example.config.AKLIMA_VOICE_NAME
import com.example.config.AppConstants
import com.example.config.CharacterRegistry
import com.example.screenshare.ScreenChangeDetector
import com.example.state.AssistantState
import com.example.state.ChatStore
import com.example.state.ConnectionState
import com.example.state.VisualState
import com.example.state.deriveVisualState
import com.example.tools.ToolManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentsAiCoreTest {

    @Test
    fun femaleVoice_isExplicitlyLocked() {
        val maleVoices = setOf("Puck", "Charon", "Fenrir", "Orus")
        assertEquals("Aoede", AKLIMA_VOICE_NAME)
        assertFalse(AKLIMA_VOICE_NAME in maleVoices)
        assertEquals(AKLIMA_VOICE_NAME, CharacterRegistry.aklima.voiceName)
        assertTrue(AKLIMA_SYSTEM_INSTRUCTION.contains("female voice", ignoreCase = true))
    }

    @Test
    fun visualState_mapsFromAssistantAndConnectionStates() {
        assertEquals(VisualState.IDLE, deriveVisualState(AssistantState.IDLE, ConnectionState.DISCONNECTED))
        assertEquals(VisualState.IDLE, deriveVisualState(AssistantState.LISTENING, ConnectionState.CONNECTED))
        assertEquals(VisualState.IDLE, deriveVisualState(AssistantState.ERROR, ConnectionState.DISCONNECTED))
        assertEquals(VisualState.THINKING, deriveVisualState(AssistantState.THINKING, ConnectionState.CONNECTED))
        assertEquals(VisualState.THINKING, deriveVisualState(AssistantState.THINKING, ConnectionState.CONNECTING))
        assertEquals(VisualState.SPEAKING, deriveVisualState(AssistantState.SPEAKING, ConnectionState.CONNECTED))
    }

    @Test
    fun toolUrlValidation_blocksUnsafeSchemesAndAllowsHttps() {
        assertTrue(ToolManager.validateUrl("https://youtube.com").isSuccess)
        assertTrue(ToolManager.validateUrl("http://example.org/docs").isSuccess)
        assertTrue(ToolManager.validateUrl("javascript:alert(1)").isFailure)
        assertTrue(ToolManager.validateUrl("data:text/html,hi").isFailure)
        assertTrue(ToolManager.validateUrl("file:///sdcard/secret").isFailure)
        assertTrue(ToolManager.validateUrl("").isFailure)
    }

    @Test
    fun chatStore_capsMemoryAt30MessagesAndClears() {
        val store = ChatStore()
        repeat(40) { idx ->
            store.appendUserText("Message $idx")
        }
        assertEquals(AppConstants.MAX_CHAT_MESSAGES, store.messages.value.size)
        assertEquals("Message 10", store.messages.value.first().text)
        assertEquals("Message 39", store.messages.value.last().text)
        store.clear()
        assertTrue(store.messages.value.isEmpty())
    }

    @Test
    fun screenChangeDetector_skipsIdenticalFramesAndForcesWhenRequested() {
        val detector = ScreenChangeDetector()
        val bmp1 = Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        val bmp2 = Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        val bmp3 = Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }

        // First frame always sent
        assertTrue(detector.shouldSendFrame(bmp1, nowMs = 1000L, lastSentAtMs = 0L, forceSend = false))
        // Identical frame within stale window skipped
        assertFalse(detector.shouldSendFrame(bmp2, nowMs = 2000L, lastSentAtMs = 1000L, forceSend = false))
        // Forced send allows identical frame
        assertTrue(detector.shouldSendFrame(bmp2, nowMs = 2500L, lastSentAtMs = 1000L, forceSend = true))
        // Changed frame sent immediately
        assertTrue(detector.shouldSendFrame(bmp3, nowMs = 3000L, lastSentAtMs = 2500L, forceSend = false))
        // Stale window exceeded triggers keep-alive send
        assertTrue(detector.shouldSendFrame(bmp3, nowMs = 12_000L, lastSentAtMs = 3000L, forceSend = false))
    }
}
