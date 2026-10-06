package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accessibility.GenericChatAdapter
import com.example.accessibility.LoopPreventionManager
import com.example.ai.AiResponseValidator
import com.example.core.model.ChatMessage
import com.example.core.model.OperatingMode
import com.example.core.model.ProcessingState
import com.example.core.state.LiveSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Live AI Reply", appName)
    }

    @Test
    fun testAiResponseValidatorValid() {
        val valid = "Yeah, I will definitely be there around 6 PM! See you soon."
        val result = AiResponseValidator.validate(valid, maxCharacters = 300)
        assertTrue(result is AiResponseValidator.ValidationResult.Valid)
    }

    @Test
    fun testAiResponseValidatorRejectsEmpty() {
        val result = AiResponseValidator.validate("", maxCharacters = 300)
        assertTrue(result is AiResponseValidator.ValidationResult.Invalid)
        assertEquals("AI response is empty.", (result as AiResponseValidator.ValidationResult.Invalid).reason)
    }

    @Test
    fun testAiResponseValidatorRejectsRoboticBoilerplate() {
        val robotic = "As an AI language model, I cannot go to your party."
        val result = AiResponseValidator.validate(robotic, maxCharacters = 300)
        assertTrue(result is AiResponseValidator.ValidationResult.Invalid)
        assertTrue((result as AiResponseValidator.ValidationResult.Invalid).reason.contains("robotic"))
    }

    @Test
    fun testAiResponseValidatorRejectsMalformedJson() {
        val malformed = "{\"error\": {\"message\": \"Rate limit reached\"}}"
        val result = AiResponseValidator.validate(malformed, maxCharacters = 300)
        assertTrue(result is AiResponseValidator.ValidationResult.Invalid)
    }

    @Test
    fun testCleanResponse() {
        val quoted = "\"Hey what's up!\""
        assertEquals("Hey what's up!", AiResponseValidator.cleanResponse(quoted))

        val prefixed = "Me: Heading out now."
        assertEquals("Heading out now.", AiResponseValidator.cleanResponse(prefixed))
    }

    @Test
    fun testLoopPreventionIncomingToOutgoingCycle() {
        val manager = LoopPreventionManager()

        // 1. New incoming message
        val incoming = ChatMessage("msg1", "Are you free tonight?", isIncoming = true)
        assertTrue("Initial incoming message should be processed", manager.shouldProcessMessage(incoming))
        manager.markMessageProcessed(incoming.text)

        // 2. Exact same incoming message repeated (due to scroll or rapid UI redraw)
        assertFalse("Repeated incoming message should not be processed again", manager.shouldProcessMessage(incoming))

        // 3. AI generates and sends a reply
        val sentReply = "Yeah, totally free! Want to hang out?"
        manager.recordSentReply(sentReply)

        // 4. Accessibility tree updates and now contains our own sent reply
        assertTrue("Manager should recognize our sent message", manager.isOurOwnSentMessage(sentReply))

        // 5. If our sent reply was accidentally parsed as an incoming bubble, shouldProcessMessage must reject it!
        val sentBubbleAsIncoming = ChatMessage("msg2", sentReply, isIncoming = true)
        assertFalse("Sent reply must never trigger a new reply", manager.shouldProcessMessage(sentBubbleAsIncoming))

        // 6. Next legitimate incoming message
        val nextIncoming = ChatMessage("msg3", "Awesome, let's meet at 8!", isIncoming = true)
        assertTrue("Subsequent distinct incoming message should be processed", manager.shouldProcessMessage(nextIncoming))
    }

    @Test
    fun testLiveSessionStateTransitions() {
        LiveSessionState.updateState(ProcessingState.MONITORING)
        assertEquals(ProcessingState.MONITORING, LiveSessionState.processingState.value)

        LiveSessionState.setNewIncomingMessage("Can you help me?", emptyList())
        assertEquals("Can you help me?", LiveSessionState.latestIncomingMessage.value)

        LiveSessionState.setGeneratedReply("Sure, what do you need?")
        assertEquals(ProcessingState.REPLY_READY, LiveSessionState.processingState.value)
        assertEquals("Sure, what do you need?", LiveSessionState.latestGeneratedReply.value)

        LiveSessionState.markReplySent("Sure, what do you need?")
        assertEquals(ProcessingState.MONITORING, LiveSessionState.processingState.value)
        assertEquals(null, LiveSessionState.latestGeneratedReply.value)
    }
}
