package com.hawas.study.dofaati.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hawas.study.dofaati.models.DifficultyLevel
import com.hawas.study.dofaati.models.DofaatiFlashcard
import com.hawas.study.dofaati.models.DofaatiQuestionItem
import com.hawas.study.dofaati.models.QuestionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "dofaati_questions")
data class DofaatiQuestionEntity(
    @PrimaryKey val id: String,
    val questionNumber: Int,
    val subjectName: String,
    val questionText: String,
    val questionTypeName: String,
    val difficultyName: String,
    val optionsJoined: String,
    val correctOptionIndex: Int,
    val circuitDiagramType: String?,
    val detailedExplanation: String,
    val sourceTag: String
) {
    fun toDomain(): DofaatiQuestionItem = DofaatiQuestionItem(
        id = id,
        questionNumber = questionNumber,
        subjectName = subjectName,
        questionText = questionText,
        questionType = runCatching { QuestionType.valueOf(questionTypeName) }.getOrDefault(QuestionType.MCQ),
        difficulty = runCatching { DifficultyLevel.valueOf(difficultyName) }.getOrDefault(DifficultyLevel.MEDIUM),
        options = optionsJoined.split(OPTION_DELIMITER).filter { it.isNotEmpty() },
        correctOptionIndex = correctOptionIndex,
        circuitDiagramType = circuitDiagramType,
        detailedExplanation = detailedExplanation,
        sourceTag = sourceTag
    )

    companion object {
        private const val OPTION_DELIMITER = "\u001F"

        fun fromDomain(item: DofaatiQuestionItem): DofaatiQuestionEntity = DofaatiQuestionEntity(
            id = item.id,
            questionNumber = item.questionNumber,
            subjectName = item.subjectName,
            questionText = item.questionText,
            questionTypeName = item.questionType.name,
            difficultyName = item.difficulty.name,
            optionsJoined = item.options.joinToString(OPTION_DELIMITER),
            correctOptionIndex = item.correctOptionIndex,
            circuitDiagramType = item.circuitDiagramType,
            detailedExplanation = item.detailedExplanation,
            sourceTag = item.sourceTag
        )
    }
}

@Entity(tableName = "dofaati_flashcards")
data class DofaatiFlashcardEntity(
    @PrimaryKey val id: String,
    val sourceTitle: String,
    val frontTitle: String,
    val frontSubtitle: String,
    val backTitle: String,
    val backFormula: String,
    val backExplanation: String
) {
    fun toDomain(): DofaatiFlashcard = DofaatiFlashcard(
        id = id,
        sourceTitle = sourceTitle,
        frontTitle = frontTitle,
        frontSubtitle = frontSubtitle,
        backTitle = backTitle,
        backFormula = backFormula,
        backExplanation = backExplanation
    )

    companion object {
        fun fromDomain(card: DofaatiFlashcard): DofaatiFlashcardEntity = DofaatiFlashcardEntity(
            id = card.id,
            sourceTitle = card.sourceTitle,
            frontTitle = card.frontTitle,
            frontSubtitle = card.frontSubtitle,
            backTitle = card.backTitle,
            backFormula = card.backFormula,
            backExplanation = card.backExplanation
        )
    }
}

@Dao
interface DofaatiDao {
    @Query("SELECT * FROM dofaati_questions ORDER BY questionNumber ASC")
    fun observeAllQuestions(): Flow<List<DofaatiQuestionEntity>>

    @Query("SELECT COUNT(*) FROM dofaati_questions")
    suspend fun countQuestions(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(items: List<DofaatiQuestionEntity>)

    @Query("SELECT * FROM dofaati_flashcards")
    fun observeAllFlashcards(): Flow<List<DofaatiFlashcardEntity>>

    @Query("SELECT COUNT(*) FROM dofaati_flashcards")
    suspend fun countFlashcards(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(cards: List<DofaatiFlashcardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFlashcard(card: DofaatiFlashcardEntity)

    @Query("DELETE FROM dofaati_flashcards WHERE id = :id")
    suspend fun deleteFlashcard(id: String)
}

@Database(
    entities = [DofaatiQuestionEntity::class, DofaatiFlashcardEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DofaatiDatabase : RoomDatabase() {
    abstract fun dofaatiDao(): DofaatiDao

    companion object {
        @Volatile
        private var INSTANCE: DofaatiDatabase? = null

        fun getInstance(context: Context): DofaatiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DofaatiDatabase::class.java,
                    "dofaati_isolated_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class DofaatiLocalStore(private val dao: DofaatiDao) {

    fun observeQuestions(): Flow<List<DofaatiQuestionItem>> =
        dao.observeAllQuestions().map { list -> list.map { it.toDomain() } }

    fun observeFlashcards(): Flow<List<DofaatiFlashcard>> =
        dao.observeAllFlashcards().map { list -> list.map { it.toDomain() } }

    suspend fun ensureSeeded() {
        if (dao.countQuestions() == 0) {
            dao.insertQuestions(buildInitial500CurriculumQuestions().map { DofaatiQuestionEntity.fromDomain(it) })
        }
        if (dao.countFlashcards() == 0) {
            dao.insertFlashcards(buildInitialFlashcards().map { DofaatiFlashcardEntity.fromDomain(it) })
        }
    }

    suspend fun appendQuestions(newItems: List<DofaatiQuestionItem>) {
        if (newItems.isEmpty()) return
        val existingCount = dao.countQuestions()
        val renumbered = newItems.mapIndexed { idx, item ->
            item.copy(
                id = if (item.id.isBlank()) "q_${existingCount + idx + 1}_${System.nanoTime()}" else item.id,
                questionNumber = existingCount + idx + 1
            )
        }
        dao.insertQuestions(renumbered.map { DofaatiQuestionEntity.fromDomain(it) })
    }

    suspend fun upsertFlashcard(card: DofaatiFlashcard) {
        dao.upsertFlashcard(DofaatiFlashcardEntity.fromDomain(card))
    }

    suspend fun deleteFlashcard(id: String) {
        dao.deleteFlashcard(id)
    }

    companion object {
        fun buildInitialFlashcards(): List<DofaatiFlashcard> = listOf(
            DofaatiFlashcard(
                id = "fc_1",
                sourceTitle = "ملزمة الباب الأول",
                frontTitle = "قانون أوم للدائرة المغلقة",
                frontSubtitle = "اضغط لقلب البطاقة ورؤية الصيغة والتفسير",
                backTitle = "تطبيق قانون أوم",
                backFormula = "R = V / I",
                backExplanation = "المقاومة الكهربية تساوي النسبة بين فرق الجهد عبر الموصل وشدة التيار المار فيه، وتقاس بالأوم (Ω)."
            ),
            DofaatiFlashcard(
                id = "fc_2",
                sourceTitle = "ملزمة الباب الأول",
                frontTitle = "المقاومة المكافئة على التوازي",
                frontSubtitle = "كيف نحسب مقاومة عدة فروع متصلة على التوازي؟",
                backTitle = "مجموع المقلوبات",
                backFormula = "1/Req = 1/R1 + 1/R2 + 1/R3",
                backExplanation = "في التوصيل على التوازي يثبت فرق الجهد ويتجزأ التيار، وتكون المقاومة المكافئة دائماً أصغر من أصغر مقاومة."
            ),
            DofaatiFlashcard(
                id = "fc_3",
                sourceTitle = "ملزمة الباب الأول",
                frontTitle = "قانون كيرشوف الأول (حفظ الشحنة)",
                frontSubtitle = "العلاقة بين التيارات الداخلة والخارجة عند نقطة تفرع",
                backTitle = "مجموع التيارات عند العقدة",
                backFormula = "∑ I_in = ∑ I_out",
                backExplanation = "مجموع شدة التيارات الداخلة إلى أي نقطة تفرع في دائرة كهربية يساوي مجموع شدة التيارات الخارجة منها."
            )
        )

        fun buildInitial500CurriculumQuestions(): List<DofaatiQuestionItem> {
            val result = ArrayList<DofaatiQuestionItem>(500)
            // Question #1 matching Mockup 8 exact wording & circuit diagram
            result.add(
                DofaatiQuestionItem(
                    id = "bank_q_1",
                    questionNumber = 1,
                    subjectName = "فيزيا",
                    questionText = "احسب المقاومة المكافئة للدائرة",
                    questionType = QuestionType.MCQ,
                    difficulty = DifficultyLevel.MEDIUM,
                    options = listOf(
                        "المقاومة الكلية = 2Ω",
                        "المعنى المكافئة: 6.2Ω",
                        "المثل الأعلى: 9Ω",
                        "المثل الملائم: 1.5Ω"
                    ),
                    correctOptionIndex = 1,
                    circuitDiagramType = "SERIES_PARALLEL_3",
                    detailedExplanation = "بتبسيط الفرع التوازي (3Ω مع 4Ω = 12/7 ≈ 1.71Ω) ثم جمعه مع المقاومة المتوالية 2Ω والمقاومة المكافئة للفرع RB نحصل على المقاومة المكافئة الكلية للدائرة بدقة.",
                    sourceTag = "ملزمة PDF"
                )
            )

            val circuitTemplates = listOf("SERIES_PARALLEL_3", "OHM_LOOP", "PARALLEL_BRANCH", null, null)
            val physicsTopics = listOf(
                Triple("التيار الكهربي وقانون أوم", "فيزيا", DifficultyLevel.EASY),
                Triple("توصيل المقاومات توالي وتوازي", "فيزيا", DifficultyLevel.MEDIUM),
                Triple("قانون أوم للدائرة المغلقة", "فيزيا", DifficultyLevel.HIGHER_ORDER),
                Triple("قانونا كيرشوف والشبكات الكهربية", "فيزيا", DifficultyLevel.HIGHER_ORDER),
                Triple("التركيب الإلكتروني والعناصر الانتقالية", "كيميا", DifficultyLevel.MEDIUM),
                Triple("المعايرة والحساب الكيميائي", "كيميا", DifficultyLevel.HIGHER_ORDER),
                Triple("الدعامة والحركة في الكائنات الحية", "أحياء", DifficultyLevel.EASY),
                Triple("النحو والبلاغة وتطبيقات الإعراب", "عربي", DifficultyLevel.MEDIUM)
            )

            for (num in 2..500) {
                val topic = physicsTopics[(num - 2) % physicsTopics.size]
                val subject = topic.second
                val difficulty = topic.third
                val diagram = if (subject == "فيزيا" && num % 5 == 0) circuitTemplates[(num / 5) % circuitTemplates.size] else null

                val item = when (subject) {
                    "فيزيا" -> {
                        val r1 = 2 + (num % 5)
                        val r2 = 3 + (num % 4)
                        val vSource = (r1 + r2) * 2
                        val seriesEq = r1 + r2
                        when (num % 4) {
                            0 -> DofaatiQuestionItem(
                                id = "bank_q_$num",
                                questionNumber = num,
                                subjectName = subject,
                                questionText = "دائرة كهربية تحتوي على مقاومتين ${r1}Ω و ${r2}Ω متصلتين على التوالي مع مصدر جهده ${vSource}V، ما شدة التيار المار؟",
                                questionType = QuestionType.MCQ,
                                difficulty = difficulty,
                                options = listOf("1A", "2A", "4A", "0.5A"),
                                correctOptionIndex = 1,
                                circuitDiagramType = diagram,
                                detailedExplanation = "المقاومة المكافئة للتوالي Req = $r1 + $r2 = ${seriesEq}Ω، وبذلك شدة التيار I = V / Req = $vSource / $seriesEq = 2A.",
                                sourceTag = "ملزمة PDF"
                            )
                            1 -> DofaatiQuestionItem(
                                id = "bank_q_$num",
                                questionNumber = num,
                                subjectName = subject,
                                questionText = "وحدة قياس شدة التيار الكهربي المكافئة لـ كولوم/ثانية هي:",
                                questionType = QuestionType.MCQ,
                                difficulty = DifficultyLevel.EASY,
                                options = listOf("الفولت", "الأمبير", "الأوم", "الوات"),
                                correctOptionIndex = 1,
                                circuitDiagramType = diagram,
                                detailedExplanation = "شدة التيار I = Q / t، فتُقاس بالكولوم لكل ثانية وهو ما يكافئ الأمبير (A).",
                                sourceTag = "تفريغ ريكورد"
                            )
                            2 -> DofaatiQuestionItem(
                                id = "bank_q_$num",
                                questionNumber = num,
                                subjectName = subject,
                                questionText = "عند زيادة طول سلك معدني إلى الضعف مع ثبوت مساحته ودرجة حرارته فإن مقاومته النوعية:",
                                questionType = QuestionType.MCQ,
                                difficulty = difficulty,
                                options = listOf("تزداد للضعف", "تقل للنصف", "تظل ثابتة", "تزداد أربع مرات"),
                                correctOptionIndex = 2,
                                circuitDiagramType = null,
                                detailedExplanation = "المقاومة النوعية (ρe) خاصية فيزيائية مميزة للمادة تتوقف فقط على نوع المادة ودرجة الحرارة ولا تتغير بتغير الطول أو المساحة.",
                                sourceTag = "بنك الوزارة"
                            )
                            else -> DofaatiQuestionItem(
                                id = "bank_q_$num",
                                questionNumber = num,
                                subjectName = subject,
                                questionText = "في الدائرة الموضحة، إذا وصلت مقاومتان متساويتان قيمة كل منهما ${r1 * 2}Ω على التوازي، تكون المقاومة المكافئة لهما:",
                                questionType = QuestionType.MCQ,
                                difficulty = difficulty,
                                options = listOf("${r1 * 4}Ω", "${r1}Ω", "${r1 * 2}Ω", "${r1 / 2}Ω"),
                                correctOptionIndex = 1,
                                circuitDiagramType = diagram,
                                detailedExplanation = "عند توصيل مقاومتين متساويتين على التوازي: Req = R / 2 = ${r1 * 2} / 2 = ${r1}Ω.",
                                sourceTag = "ملزمة PDF"
                            )
                        }
                    }
                    "كيميا" -> DofaatiQuestionItem(
                        id = "bank_q_$num",
                        questionNumber = num,
                        subjectName = subject,
                        questionText = "أي العناصر الانتقالية التالية يمتلك أعلى حالة تأكسد مستقرة في السلسلة الانتقالية الأولى؟",
                        questionType = QuestionType.MCQ,
                        difficulty = difficulty,
                        options = listOf("السكانديوم Sc", "المنجنيز Mn", "الحديد Fe", "النحاس Cu"),
                        correctOptionIndex = 1,
                        circuitDiagramType = null,
                        detailedExplanation = "يصل عنصر المنجنيز (₂₅Mn) إلى حالة التأكسد +7 عند فقد جميع إلكترونات المستويين 4s² 3d⁵.",
                        sourceTag = "ملزمة PDF"
                    )
                    "أحياء" -> DofaatiQuestionItem(
                        id = "bank_q_$num",
                        questionNumber = num,
                        subjectName = subject,
                        questionText = "الوحدة الوظيفية الأساسية للانقباض العضلي في العضلة الهيكلية هي:",
                        questionType = QuestionType.MCQ,
                        difficulty = difficulty,
                        options = listOf("اللييفة العضلية", "القطعة العضلية (الساركومير)", "خيط الميوسين", "الساركوليما"),
                        correctOptionIndex = 1,
                        circuitDiagramType = null,
                        detailedExplanation = "الساركومير هو المسافة بين كل خطين متتاليين Z ويمثل أصغر وحدة وظيفية قادرة على الانقباض العضلي.",
                        sourceTag = "ملزمة PDF"
                    )
                    else -> DofaatiQuestionItem(
                        id = "bank_q_$num",
                        questionNumber = num,
                        subjectName = subject,
                        questionText = "في جملة «إنما المؤمنون إخوة»، ما إعراب كلمة (المؤمنون) بعد دخول (ما) الكافة على (إن)؟",
                        questionType = QuestionType.MCQ,
                        difficulty = difficulty,
                        options = listOf("اسم إن منصوب", "مبتدأ مرفوع بالواو", "فاعل مرفوع", "خبر مرفوع"),
                        correctOptionIndex = 1,
                        circuitDiagramType = null,
                        detailedExplanation = "دخول (ما) الكافة على (إن) يبطل عملها في الجملة الاسمية، فيُعرب ما بعدها مبتدأ مرفوعاً وعلامة رفعه الواو لأنه جمع مذكر سالم.",
                        sourceTag = "لصق المشرف"
                    )
                }
                result.add(item)
            }
            return result
        }
    }
}
