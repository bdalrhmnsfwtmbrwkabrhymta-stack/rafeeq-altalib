package com.example.domain.dictionary

import android.content.Context
import android.util.Log
import com.example.data.database.DictionaryWordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader

data class CurriculumWord(
    val en: String,
    val ar: String,
    val pos: String,
    val phonetic: String,
    val ex: String
)

data class CommonTopicWord(
    val en: String,
    val ar: String,
    val topic: String
)

data class ConfusablePair(
    val pair: String,
    val explanationAr: String
)

data class CollocationEntry(
    val verb: String,
    val phrase: String,
    val ar: String
)

data class PrepositionEntry(
    val expr: String,
    val ar: String,
    val type: String
)

data class IrregularVerbEntry(
    val present: String,
    val past: String,
    val pp: String,
    val ar: String
)

data class TranslationTermEntry(
    val en: String,
    val ar: String,
    val topic: String
)

data class OfflineDictionaryBundle(
    val curriculumWords: List<CurriculumWord>,
    val commonWords: List<CommonTopicWord>,
    val confusables: List<ConfusablePair>,
    val collocations: List<CollocationEntry>,
    val prepositions: List<PrepositionEntry>,
    val irregularVerbs: List<IrregularVerbEntry>,
    val translationTerms: List<TranslationTermEntry>
) {
    val totalItemCount: Int
        get() = curriculumWords.size + commonWords.size + confusables.size +
            collocations.size + prepositions.size + irregularVerbs.size + translationTerms.size
}

object DictionaryAssetLoader {
    private const val TAG = "DictionaryAssetLoader"

    @Volatile
    private var cachedBundle: OfflineDictionaryBundle? = null

    suspend fun loadBundle(context: Context): OfflineDictionaryBundle = withContext(Dispatchers.IO) {
        cachedBundle?.let { return@withContext it }

        val appContext = context.applicationContext
        val allWords = mutableListOf<CurriculumWord>()
        val seenWords = HashSet<String>(4096)

        for (i in 1..6) {
            val arr = readJsonArray(appContext, "dictionary/all_words_$i.json")
            for (idx in 0 until arr.length()) {
                val obj = arr.optJSONObject(idx) ?: continue
                val en = obj.optString("en").trim()
                val ar = obj.optString("ar").trim()
                if (en.isEmpty() || ar.isEmpty()) continue
                val key = en.lowercase()
                if (seenWords.add(key)) {
                    allWords.add(
                        CurriculumWord(
                            en = en,
                            ar = ar,
                            pos = obj.optString("pos").trim(),
                            phonetic = obj.optString("phonetic").trim(),
                            ex = obj.optString("ex").trim()
                        )
                    )
                }
            }
        }

        val commonWords = mutableListOf<CommonTopicWord>()
        run {
            val arr = readJsonArray(appContext, "dictionary/common_words.json")
            for (idx in 0 until arr.length()) {
                val obj = arr.optJSONObject(idx) ?: continue
                val en = obj.optString("en").trim()
                val ar = obj.optString("ar").trim()
                val topic = obj.optString("topic").trim()
                if (en.isNotEmpty() && ar.isNotEmpty()) {
                    commonWords.add(CommonTopicWord(en = en, ar = ar, topic = topic))
                }
            }
        }

        val confusables = mutableListOf<ConfusablePair>()
        for (i in 1..2) {
            val arr = readJsonArray(appContext, "dictionary/confusables_$i.json")
            for (idx in 0 until arr.length()) {
                val obj = arr.optJSONObject(idx) ?: continue
                val pair = obj.optString("pair").trim()
                val exp = obj.optString("explanationAr").trim()
                if (pair.isNotEmpty() && exp.isNotEmpty()) {
                    confusables.add(ConfusablePair(pair = pair, explanationAr = exp))
                }
            }
        }

        val collocations = mutableListOf<CollocationEntry>()
        run {
            val arr = readJsonArray(appContext, "dictionary/collocations.json")
            for (idx in 0 until arr.length()) {
                val obj = arr.optJSONObject(idx) ?: continue
                val verb = obj.optString("verb").trim()
                val phrase = obj.optString("phrase").trim()
                val ar = obj.optString("ar").trim()
                if (phrase.isNotEmpty() && ar.isNotEmpty()) {
                    collocations.add(CollocationEntry(verb = verb, phrase = phrase, ar = ar))
                }
            }
        }

        val prepositions = mutableListOf<PrepositionEntry>()
        run {
            val arr = readJsonArray(appContext, "dictionary/prepositions.json")
            for (idx in 0 until arr.length()) {
                val obj = arr.optJSONObject(idx) ?: continue
                val expr = obj.optString("expr").trim()
                val ar = obj.optString("ar").trim()
                val type = obj.optString("type").trim()
                if (expr.isNotEmpty() && ar.isNotEmpty()) {
                    prepositions.add(PrepositionEntry(expr = expr, ar = ar, type = type))
                }
            }
        }

        val irregularVerbs = mutableListOf<IrregularVerbEntry>()
        run {
            val arr = readJsonArray(appContext, "dictionary/irregular_verbs.json")
            for (idx in 0 until arr.length()) {
                val obj = arr.optJSONObject(idx) ?: continue
                val present = obj.optString("present").trim()
                val past = obj.optString("past").trim()
                val pp = obj.optString("pp").trim()
                val ar = obj.optString("ar").trim()
                if (present.isNotEmpty() && ar.isNotEmpty()) {
                    irregularVerbs.add(IrregularVerbEntry(present = present, past = past, pp = pp, ar = ar))
                }
            }
        }

        val translationTerms = mutableListOf<TranslationTermEntry>()
        run {
            val arr = readJsonArray(appContext, "dictionary/translation_terms.json")
            for (idx in 0 until arr.length()) {
                val obj = arr.optJSONObject(idx) ?: continue
                val en = obj.optString("en").trim()
                val ar = obj.optString("ar").trim()
                val topic = obj.optString("topic").trim()
                if (en.isNotEmpty() && ar.isNotEmpty()) {
                    translationTerms.add(TranslationTermEntry(en = en, ar = ar, topic = topic))
                }
            }
        }

        OfflineDictionaryBundle(
            curriculumWords = allWords,
            commonWords = commonWords,
            confusables = confusables,
            collocations = collocations,
            prepositions = prepositions,
            irregularVerbs = irregularVerbs,
            translationTerms = translationTerms
        ).also {
            cachedBundle = it
        }
    }

    suspend fun loadAllEntitiesForDatabase(context: Context): List<DictionaryWordEntity> = withContext(Dispatchers.IO) {
        val bundle = loadBundle(context)
        val merged = LinkedHashMap<String, DictionaryWordEntity>(5000)

        fun putIfAbsent(entity: DictionaryWordEntity) {
            val key = entity.wordEnglish.lowercase().trim()
            if (key.isNotEmpty() && !merged.containsKey(key)) {
                merged[key] = entity
            }
        }

        bundle.curriculumWords.forEach { w ->
            putIfAbsent(
                DictionaryWordEntity(
                    wordEnglish = w.en,
                    wordArabic = w.ar,
                    phonetic = w.phonetic.removeSurrounding("/"),
                    partOfSpeech = w.pos,
                    exampleSentence = w.ex,
                    emoji = posToEmoji(w.pos),
                    category = "المنهج الشامل"
                )
            )
        }

        bundle.commonWords.forEach { c ->
            putIfAbsent(
                DictionaryWordEntity(
                    wordEnglish = c.en,
                    wordArabic = c.ar,
                    phonetic = "",
                    partOfSpeech = "vocab",
                    exampleSentence = "",
                    emoji = "📌",
                    category = c.topic.ifBlank { "كلمات شائعة" }
                )
            )
        }

        bundle.translationTerms.forEach { t ->
            putIfAbsent(
                DictionaryWordEntity(
                    wordEnglish = t.en,
                    wordArabic = t.ar,
                    phonetic = "",
                    partOfSpeech = "term",
                    exampleSentence = "",
                    emoji = "🌐",
                    category = "ترجمة: ${t.topic.ifBlank { "عام" }}"
                )
            )
        }

        bundle.irregularVerbs.forEach { v ->
            putIfAbsent(
                DictionaryWordEntity(
                    wordEnglish = v.present,
                    wordArabic = v.ar,
                    phonetic = "${v.past} / ${v.pp}",
                    partOfSpeech = "irregular verb",
                    exampleSentence = "${v.present} → ${v.past} → ${v.pp}",
                    emoji = "⚡",
                    category = "أفعال شاذة"
                )
            )
        }

        bundle.prepositions.forEach { p ->
            putIfAbsent(
                DictionaryWordEntity(
                    wordEnglish = p.expr,
                    wordArabic = p.ar,
                    phonetic = "",
                    partOfSpeech = p.type.ifBlank { "prep" },
                    exampleSentence = "",
                    emoji = "🔗",
                    category = "حروف الجر والتعبيرات"
                )
            )
        }

        bundle.collocations.forEach { col ->
            putIfAbsent(
                DictionaryWordEntity(
                    wordEnglish = col.phrase,
                    wordArabic = col.ar,
                    phonetic = col.verb,
                    partOfSpeech = "collocation",
                    exampleSentence = "",
                    emoji = "🧩",
                    category = "متلازمات لفظية"
                )
            )
        }

        DictionaryDataGenerator.generateAllWords().forEach { baseWord ->
            putIfAbsent(baseWord)
        }

        merged.values.sortedBy { it.wordEnglish.lowercase() }
    }

    private fun posToEmoji(pos: String): String = when (pos.lowercase()) {
        "noun" -> "📘"
        "verb" -> "⚡"
        "adj" -> "🎨"
        "adv" -> "✨"
        "prep" -> "🔗"
        "conj" -> "🔀"
        "pron" -> "👤"
        else -> "📖"
    }

    private fun readJsonArray(context: Context, assetPath: String): JSONArray {
        return try {
            val json = context.assets.open(assetPath).bufferedReader().use(BufferedReader::readText)
            JSONArray(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading asset $assetPath: ${e.message}")
            JSONArray()
        }
    }
}
