package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.core.model.ChatMessage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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

            val messages = mutableListOf<ChatMessage>()
            val imageWidth = targetBitmap.width

            for (block in visionText.textBlocks) {
                val box = block.boundingBox ?: Rect()
                val text = block.text.trim()
                if (text.length < 2) continue

                // Exclude status bar and soft nav bar text if full screen
                if (box.top < 100 || box.bottom > targetBitmap.height - 100) continue

                // Check left vs right alignment in conversation area
                val centerX = box.centerX()
                val isIncoming = centerX < (imageWidth * 0.52f)

                messages.add(
                    ChatMessage(
                        id = "ocr_${box.left}_${box.top}_${text.hashCode()}",
                        text = text,
                        isIncoming = isIncoming,
                        timestamp = System.currentTimeMillis(),
                        confidence = 0.85f,
                        boundsLeft = box.left,
                        boundsRight = box.right
                    )
                )
            }

            // Sort vertically by top coordinate
            messages.sortedBy { it.boundsLeft }
        } catch (e: Exception) {
            emptyList()
        } finally {
            if (targetBitmap != bitmap && !targetBitmap.isRecycled) {
                targetBitmap.recycle()
            }
        }
    }
}
