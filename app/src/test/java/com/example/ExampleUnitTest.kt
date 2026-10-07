package com.example

import com.example.accessibility.ChatReaderEngine
import com.example.core.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testSystemNoiseFiltering() {
    // End-to-end encryption banners
    assertTrue(ChatReaderEngine.isSystemNoise("Messages and calls are end-to-end encrypted"))
    assertTrue(ChatReaderEngine.isSystemNoise("cifrados de extremo a extremo"))
    assertTrue(ChatReaderEngine.isSystemNoise("chiffrés de bout en bout"))

    // Dates and times
    assertTrue(ChatReaderEngine.isSystemNoise("TODAY"))
    assertTrue(ChatReaderEngine.isSystemNoise("yesterday"))
    assertTrue(ChatReaderEngine.isSystemNoise("10:45 AM"))
    assertTrue(ChatReaderEngine.isSystemNoise("23:59"))

    // UI chrome & overlay controls
    assertTrue(ChatReaderEngine.isSystemNoise("Type a message"))
    assertTrue(ChatReaderEngine.isSystemNoise("Message ChatGPT"))
    assertTrue(ChatReaderEngine.isSystemNoise("Live AI Reply"))

    // Valid human chat messages must NOT be filtered
    assertFalse(ChatReaderEngine.isSystemNoise("Hey, are you free today?"))
    assertFalse(ChatReaderEngine.isSystemNoise("Can you send me the report?"))
    assertFalse(ChatReaderEngine.isSystemNoise("Sounds good to me!"))
  }

  @Test
  fun testBurstMessageExtraction() {
    val messages = listOf(
      ChatMessage("1", "Hey there!", isIncoming = false),
      ChatMessage("2", "Good morning!", isIncoming = true, senderName = "Sarah"),
      ChatMessage("3", "Are you available to chat?", isIncoming = true, senderName = "Sarah"),
      ChatMessage("4", "Have a quick question about tomorrow.", isIncoming = true, senderName = "Sarah")
    )

    val burst = ChatReaderEngine.extractLatestIncomingBurst(messages)
    assertNotNull(burst)
    assertEquals(3, burst?.individualMessages?.size)
    assertEquals("Sarah", burst?.primarySender)
    assertTrue(burst?.combinedText?.contains("Good morning!") == true)
    assertTrue(burst?.combinedText?.contains("Are you available to chat?") == true)
    assertTrue(burst?.combinedText?.contains("Have a quick question about tomorrow.") == true)
  }

  @Test
  fun testQuotedMessageFormatting() {
    val quotedMessage = ChatMessage(
      id = "5",
      text = "Yes, 4 PM works best!",
      isIncoming = true,
      senderName = "Alex",
      replyToText = "What time do you prefer?",
      replyToSender = "You"
    )

    val formatted = quotedMessage.formatForAi()
    assertTrue(formatted.contains("[Alex]"))
    assertTrue(formatted.contains("(replying to You: \"What time do you prefer?\")"))
    assertTrue(formatted.contains("Yes, 4 PM works best!"))
  }

  @Test
  fun testGroupChatDetection() {
    val groupMessages = listOf(
      ChatMessage("1", "Hey all!", isIncoming = true, senderName = "Alice"),
      ChatMessage("2", "Hello!", isIncoming = true, senderName = "Bob"),
      ChatMessage("3", "Count me in", isIncoming = false)
    )

    assertTrue(ChatReaderEngine.isGroupChat(groupMessages, "Weekend Hike"))
  }
}
