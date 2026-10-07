package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.accessibility.ChatReaderEngine
import com.example.core.model.ChatMessage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Advanced On-Screen OCR Chat Reading Engine powered by ML Kit.
 *
 * Implements:
 * - Smart screen boundary pruning (status bar, bottom input chrome)
 * - Deep multilingual noise suppression & floating overlay window exclusion
 * - Header sender extraction for group chats
 * - Bubble clustering with spatial alignment (left vs right)
 */
class OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeConversation(
        bitmap: Bitmap,
        cropRect: Rect? = null
    ): List<ChatMessage> = withContext(Dispatchers.Default) {
        val targetBitmap = if (cropRect != null && cropRect.width() > 50 && cropRect.height() > 50) {
            val safeLeft = cropRect.left.coerceIn(0, bitmap.width - 1)
            val safeTop = cropRect.top.coerceIn(0, bitmap.height - 1)
            val safeWidth = cropRect.width().coerceAtMost(bitmap.width - safeLeft)
            val safeHeight = cropRect.height().coerceAtMost(bitmap.height - safeTop)
            Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
        } else {
            bitmap
        }

        try {
            val inputImage = InputImage.fromBitmap(targetBitmap, 0)
            val visionText = recognizer.process(inputImage).await()

            val rawBlocks = mutableListOf<RawTextBlock>()
            val imageHeight = targetBitmap.height
            val imageWidth = targetBitmap.width

            // Dynamic boundary margins to discard status bar and bottom input controls
            val topMargin = (imageHeight * 0.08f).toInt()
            val bottomMargin = (imageHeight * 0.88f).toInt()

            for (block in visionText.textBlocks) {
                val box = block.boundingBox ?: Rect()
                val text = cleanOcrText(block.text)

                if (text.length < 2) continue

                // Exclude status bar (top 8%) and bottom input area (bottom 12%)
                if (box.top < topMargin || box.bottom > bottomMargin) continue

                // Exclude system noise, lone timestamps, and UI placeholders
                if (ChatReaderEngine.isSystemNoise(text)) continue

                // Determine bubble alignment
                // Right aligned (outgoing): box.right > 65% width and box.left > 22% width
                val isRightAligned = (box.right > imageWidth * 0.65f) && (box.left > imageWidth * 0.22f)
                val isIncoming = !isRightAligned

                // Check for sender name prefix (e.g. "Alex: hello" or bold first line in block)
                val (sender, messageText) = extractSenderFromOcrText(text, isIncoming)

                rawBlocks.add(
                    RawTextBlock(
                        text = messageText,
                        senderName = sender,
                        box = box,
                        isIncoming = isIncoming
                    )
                )
            }

            if (rawBlocks.isEmpty()) return@withContext emptyList()

            // Sort vertically in screen reading order
            rawBlocks.sortBy { it.box.top }

            // Smart Bubble Clustering: Merge vertically adjacent blocks that belong to the same chat bubble
            val clusteredMessages = clusterTextBlocksIntoMessages(rawBlocks, imageHeight)

            clusteredMessages
        } catch (e: Exception) {
            emptyList()
        } finally {
            if (targetBitmap != bitmap && !targetBitmap.isRecycled) {
                try {
                    targetBitmap.recycle()
                } catch (ignored: Throwable) {}
            }
        }
    }

    private fun cleanOcrText(raw: String): String {
        return raw.lines()
            .map { it.trim() }
            .filter { line -> line.isNotEmpty() && !ChatReaderEngine.isSystemNoise(line) }
            .joinToString(" ")
            .trim()
    }

    private fun extractSenderFromOcrText(text: String, isIncoming: Boolean): Pair<String?, String> {
        if (!isIncoming) return Pair(null, text)

        val colonIndex = text.indexOf(':')
        if (colonIndex in 2..25) {
            val potentialSender = text.substring(0, colonIndex).trim()
            val remainder = text.substring(colonIndex + 1).trim()
            // Make sure the sender doesn't contain digits or time indicators
            if (potentialSender.all { it.isLetter() || it.isWhitespace() } && remainder.isNotBlank()) {
                return Pair(potentialSender, remainder)
            }
        }
        return Pair(null, text)
    }

    /**
     * Clusters lines belonging to the same bubble (small vertical distance and same alignment).
     */
    private fun clusterTextBlocksIntoMessages(
        blocks: List<RawTextBlock>,
        imageHeight: Int
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        var currentCluster: MutableList<RawTextBlock>? = null

        val maxVerticalGap = (imageHeight * 0.045f).toInt().coerceAtLeast(25)

        for (block in blocks) {
            if (currentCluster == null) {
                currentCluster = mutableListOf(block)
                continue
            }

            val lastBlock = currentCluster.last()
            val verticalGap = block.box.top - lastBlock.box.bottom
            val sameAlignment = block.isIncoming == lastBlock.isIncoming

            if (sameAlignment && verticalGap in -10..maxVerticalGap) {
                // Same bubble continuation
                currentCluster.add(block)
            } else {
                // Finalize previous cluster
                messages.add(buildMessageFromCluster(currentCluster))
                currentCluster = mutableListOf(block)
            }
        }

        if (!currentCluster.isNullOrEmpty()) {
            messages.add(buildMessageFromCluster(currentCluster))
        }

        return messages
    }

    private fun buildMessageFromCluster(cluster: List<RawTextBlock>): ChatMessage {
        val combinedText = cluster.joinToString(" ") { it.text }.trim()
        val sender = cluster.firstOrNull { !it.senderName.isNullOrBlank() }?.senderName
        val firstBox = cluster.first().box
        val lastBox = cluster.last().box
        val isIncoming = cluster.first().isIncoming

        val minLeft = cluster.minOf { it.box.left }
        val maxRight = cluster.maxOf { it.box.right }

        return ChatMessage(
            id = "ocr_${firstBox.left}_${firstBox.top}_${combinedText.hashCode()}",
            text = combinedText,
            isIncoming = isIncoming,
            senderName = sender,
            timestamp = System.currentTimeMillis(),
            confidence = 0.92f,
            boundsLeft = minLeft,
            boundsTop = firstBox.top,
            boundsRight = maxRight,
            boundsBottom = lastBox.bottom
        )
    }

    private data class RawTextBlock(
        val text: String,
        val senderName: String? = null,
        val box: Rect,
        val isIncoming: Boolean
    )
}
