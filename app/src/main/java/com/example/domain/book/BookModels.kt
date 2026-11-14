package com.example.domain.book

import com.example.domain.ai.GeneratedFlashcard
import org.json.JSONArray
import org.json.JSONObject

data class MemoryBookItem(
    val name: String,
    val mechanism: String,
    val benefit: String,
    val example: String? = null,
    val tip: String? = null
)

data class MemoryBookChapter(
    val id: String,
    val title: String,
    val intro: String,
    val items: List<MemoryBookItem>,
    val summary: String
)

data class MemoryBookData(
    val bookTitle: String,
    val introTitle: String,
    val introBody: String,
    val chapters: List<MemoryBookChapter>,
    val conclusionTitle: String,
    val conclusionBody: String
)

data class BookProcessingResult(
    val bookData: MemoryBookData,
    val importantPoints: List<String>,
    val flashcards: List<GeneratedFlashcard>
)

object BookJsonSerializer {

    fun serializeBookData(data: MemoryBookData): String {
        val root = JSONObject()
        root.put("bookTitle", data.bookTitle)
        root.put("introTitle", data.introTitle)
        root.put("introBody", data.introBody)
        root.put("conclusionTitle", data.conclusionTitle)
        root.put("conclusionBody", data.conclusionBody)
        val chaptersArr = JSONArray()
        for (ch in data.chapters) {
            val chObj = JSONObject()
            chObj.put("id", ch.id)
            chObj.put("title", ch.title)
            chObj.put("intro", ch.intro)
            chObj.put("summary", ch.summary)
            val itemsArr = JSONArray()
            for (item in ch.items) {
                val itemObj = JSONObject()
                itemObj.put("name", item.name)
                itemObj.put("mechanism", item.mechanism)
                itemObj.put("benefit", item.benefit)
                if (item.example != null) itemObj.put("example", item.example)
                itemsArr.put(itemObj)
            }
            chObj.put("items", itemsArr)
            chaptersArr.put(chObj)
        }
        root.put("chapters", chaptersArr)
        return root.toString()
    }

    fun deserializeBookData(jsonStr: String): MemoryBookData? {
        if (jsonStr.isBlank()) return null
        return try {
            val root = JSONObject(jsonStr)
            val bookTitle = root.optString("bookTitle", "عنوان الكتاب")
            val introTitle = root.optString("introTitle", "مقدمة الكتاب")
            val introBody = root.optString("introBody", "")
            val conclusionTitle = root.optString("conclusionTitle", "خاتمة وخلاصة")
            val conclusionBody = root.optString("conclusionBody", "")
            val chaptersList = mutableListOf<MemoryBookChapter>()
            val chaptersArr = root.optJSONArray("chapters")
            if (chaptersArr != null) {
                for (i in 0 until chaptersArr.length()) {
                    val chObj = chaptersArr.getJSONObject(i)
                    val itemsList = mutableListOf<MemoryBookItem>()
                    val itemsArr = chObj.optJSONArray("items")
                    if (itemsArr != null) {
                        for (j in 0 until itemsArr.length()) {
                            val itemObj = itemsArr.getJSONObject(j)
                            itemsList.add(
                                MemoryBookItem(
                                    name = itemObj.optString("name", ""),
                                    mechanism = itemObj.optString("mechanism", ""),
                                    benefit = itemObj.optString("benefit", ""),
                                    example = if (itemObj.has("example")) itemObj.optString("example") else null
                                )
                            )
                        }
                    }
                    chaptersList.add(
                        MemoryBookChapter(
                            id = chObj.optString("id", "ch$i"),
                            title = chObj.optString("title", "الفصل"),
                            intro = chObj.optString("intro", ""),
                            items = itemsList,
                            summary = chObj.optString("summary", "")
                        )
                    )
                }
            }
            MemoryBookData(
                bookTitle = bookTitle,
                introTitle = introTitle,
                introBody = introBody,
                chapters = chaptersList,
                conclusionTitle = conclusionTitle,
                conclusionBody = conclusionBody
            )
        } catch (e: Exception) {
            null
        }
    }

    fun serializePointsList(points: List<String>): String {
        val arr = JSONArray()
        points.forEach { arr.put(it) }
        return arr.toString()
    }

    fun deserializePointsList(jsonStr: String): List<String> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}

