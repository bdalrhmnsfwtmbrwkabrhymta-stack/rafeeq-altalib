package com.example.domain.anki

import android.content.Context
import com.example.data.database.FlashcardEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AnkiSmartOcrIntegrationModule {

    /**
     * Splits extracted OCR text into intelligent Q&A flashcards for smart spaced repetition.
     */
    suspend fun generateFlashcardsFromOcrText(
        extractedText: String,
        deckId: Long,
        subjectCategory: String = "عام"
    ): List<FlashcardEntity> = withContext(Dispatchers.Default) {
        val lines = extractedText.lines().map { it.trim() }.filter { it.length > 5 }
        val cards = mutableListOf<FlashcardEntity>()

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.contains(":") || line.contains("=") || line.contains("-")) {
                val delimiter = if (line.contains(":")) ":" else if (line.contains("=")) "=" else "-"
                val parts = line.split(delimiter, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    cards.add(
                        FlashcardEntity(
                            deckId = deckId,
                            question = parts[0].trim(),
                            answer = parts[1].trim(),
                            difficulty = "MEDIUM",
                            contextStr = subjectCategory
                        )
                    )
                }
            } else if (index + 1 < lines.size) {
                cards.add(
                    FlashcardEntity(
                        deckId = deckId,
                        question = line,
                        answer = lines[index + 1],
                        difficulty = "MEDIUM",
                        contextStr = subjectCategory
                    )
                )
                index++
            }
            index++
        }

        if (cards.isEmpty() && extractedText.isNotBlank()) {
            cards.add(
                FlashcardEntity(
                    deckId = deckId,
                    question = "ملخص الفقرة الرئيسية ($subjectCategory)",
                    answer = extractedText.take(300),
                    difficulty = "MEDIUM",
                    contextStr = subjectCategory
                )
            )
        }

        cards
    }
}
