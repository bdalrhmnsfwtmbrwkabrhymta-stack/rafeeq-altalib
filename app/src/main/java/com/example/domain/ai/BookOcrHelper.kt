package com.example.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

data class OcrExtractionResult(
    val fullText: String,
    val wordCount: Int,
    val lineCount: Int,
    val blockCount: Int = 0
)

object BookOcrHelper {

    fun recognizeTextFromBitmap(
        bitmap: Bitmap,
        onSuccess: (OcrExtractionResult) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val formattedText = if (visionText.textBlocks.isNotEmpty()) {
                        visionText.textBlocks.joinToString("\n\n") { block ->
                            block.lines.joinToString("\n") { it.text }
                        }.trim()
                    } else {
                        visionText.text.trim()
                    }
                    val lines = visionText.textBlocks.flatMap { it.lines }.size
                    val words = if (formattedText.isBlank()) 0 else formattedText.split("\\s+".toRegex()).size
                    onSuccess(
                        OcrExtractionResult(
                            fullText = formattedText,
                            wordCount = words,
                            lineCount = lines,
                            blockCount = visionText.textBlocks.size
                        )
                    )
                }
                .addOnFailureListener { e ->
                    onFailure(e)
                }
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    fun recognizeTextFromUri(
        context: Context,
        uri: Uri,
        onSuccess: (OcrExtractionResult) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val formattedText = if (visionText.textBlocks.isNotEmpty()) {
                        visionText.textBlocks.joinToString("\n\n") { block ->
                            block.lines.joinToString("\n") { it.text }
                        }.trim()
                    } else {
                        visionText.text.trim()
                    }
                    val lines = visionText.textBlocks.flatMap { it.lines }.size
                    val words = if (formattedText.isBlank()) 0 else formattedText.split("\\s+".toRegex()).size
                    onSuccess(
                        OcrExtractionResult(
                            fullText = formattedText,
                            wordCount = words,
                            lineCount = lines,
                            blockCount = visionText.textBlocks.size
                        )
                    )
                }
                .addOnFailureListener { e ->
                    onFailure(e)
                }
        } catch (e: Exception) {
            onFailure(e)
        }
    }
}
