package com.example.data.repository

import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.db.*
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@JsonClass(generateAdapter = true)
data class GeminiStudyResponse(
    val summary: String,
    val eli12: String,
    val mnemonics: List<String>,
    val keyPoints: List<String>,
    val activeRecall: List<ActiveRecallCardJson>?,
    val quiz: List<QuizQuestionJson>?
)

@JsonClass(generateAdapter = true)
data class ActiveRecallCardJson(
    val question: String,
    val answer: String
)

@JsonClass(generateAdapter = true)
data class QuizQuestionJson(
    val question: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String
)

class StudyRepository(private val db: AppDatabase) {

    private val studySessionDao = db.studySessionDao()
    private val quizHistoryDao = db.quizHistoryDao()
    private val studyStreakDao = db.studyStreakDao()
    private val studyReminderDao = db.studyReminderDao()
    private val achievementBadgeDao = db.achievementBadgeDao()

    val allSessions: Flow<List<StudySession>> = studySessionDao.getAllSessions()
    val quizHistory: Flow<List<QuizHistory>> = quizHistoryDao.getAllHistory()
    val averageScore: Flow<Double?> = quizHistoryDao.getAverageScoreFlow()
    val totalQuizzes: Flow<Int> = quizHistoryDao.getTotalQuizzesFlow()
    val currentStreak: Flow<StudyStreak?> = studyStreakDao.getStreakFlow()
    val allReminders: Flow<List<StudyReminder>> = studyReminderDao.getAllReminders()
    val allBadges: Flow<List<AchievementBadge>> = achievementBadgeDao.getAllBadges()

    suspend fun populateDefaultBadges() = withContext(Dispatchers.IO) {
        val count = achievementBadgeDao.getAllBadgesDirect().size
        if (count == 0) {
            val defaults = listOf(
                AchievementBadge("first_steps", "First Contact", "Created your first custom study guide", "rocket"),
                AchievementBadge("streak_3", "Dedicated Learner", "Maintained a 3-day study streak", "timeline"),
                AchievementBadge("streak_5", "Unstoppable Force", "Maintained a 5-day study streak", "bolt"),
                AchievementBadge("midnight_oil", "Midnight Oil", "Generated a study guide in 'Exam Tomorrow' mode", "brightness_3"),
                AchievementBadge("master_mind", "Grandmaster", "Got a perfect 5/5 score on any quiz difficulty", "grade"),
                AchievementBadge("recall_champion", "Recall Pro", "Completed first active recall revision session", "psychology")
            )
            achievementBadgeDao.insertBadges(defaults)
        }
    }

    suspend fun createReminder(reminder: StudyReminder) = withContext(Dispatchers.IO) {
        studyReminderDao.insertReminder(reminder)
    }

    suspend fun deleteReminder(id: Int) = withContext(Dispatchers.IO) {
        studyReminderDao.deleteReminderById(id)
    }

    suspend fun updateReminder(reminder: StudyReminder) = withContext(Dispatchers.IO) {
        studyReminderDao.updateReminder(reminder)
    }

    suspend fun triggerStreakUpdate() = withContext(Dispatchers.IO) {
        val today = getCurrentDateString()
        val yesterday = getYesterdayDateString()
        val currentStreakRecord = studyStreakDao.getStreakDirect() ?: StudyStreak(id = 1)

        val updatedStreak = when (currentStreakRecord.lastStudyDate) {
            today -> {
                // Already studied today, keep current streak
                currentStreakRecord
            }
            yesterday -> {
                // Studied yesterday, increment streak
                val newCurrent = currentStreakRecord.currentStreak + 1
                val newMax = if (newCurrent > currentStreakRecord.maxStreak) newCurrent else currentStreakRecord.maxStreak
                currentStreakRecord.copy(currentStreak = newCurrent, maxStreak = newMax, lastStudyDate = today)
            }
            else -> {
                // Over a day break, restart streak to 1
                val newCurrent = 1
                val newMax = if (newCurrent > currentStreakRecord.maxStreak) newCurrent else currentStreakRecord.maxStreak
                currentStreakRecord.copy(currentStreak = newCurrent, maxStreak = newMax, lastStudyDate = today)
            }
        }
        studyStreakDao.insertStreak(updatedStreak)

        // Post streak streak-badge triggers
        if (updatedStreak.currentStreak >= 3) {
            unlockBadgeDirect("streak_3")
        }
        if (updatedStreak.currentStreak >= 5) {
            unlockBadgeDirect("streak_5")
        }
    }

    suspend fun saveQuizHistory(topic: String, score: Int, totalQuestions: Int, difficulty: String) = withContext(Dispatchers.IO) {
        val record = QuizHistory(
            topic = topic,
            score = score,
            totalQuestions = totalQuestions,
            difficulty = difficulty,
            timestamp = System.currentTimeMillis()
        )
        quizHistoryDao.insertHistory(record)

        // Increment or check streak with every quiz completed too!
        triggerStreakUpdate()

        // Check badge perfect scores
        if (score == totalQuestions) {
            unlockBadgeDirect("master_mind")
        }
    }

    suspend fun deleteSession(id: Int) = withContext(Dispatchers.IO) {
        studySessionDao.deleteSessionById(id)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        studySessionDao.deleteAllSessions()
        quizHistoryDao.clearHistory()
        studyStreakDao.insertStreak(StudyStreak(id = 1, currentStreak = 0, maxStreak = 0, lastStudyDate = ""))
    }

    suspend fun unlockBadgeDirect(badgeId: String) = withContext(Dispatchers.IO) {
        achievementBadgeDao.unlockBadge(badgeId, System.currentTimeMillis())
    }

    suspend fun generateStudyGuide(
        topicOrNote: String,
        difficulty: String,
        isExamTomorrow: Boolean
    ): StudySession = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API Key is missing. Please configuration your GEMINI_API_KEY inside the Secrets panel.")
        }

        // Build prompt tailored to psychology-backed learning mechanisms
        val systemPrompt = """
            You are an expert AI Study Buddy designed to make learning intuitive, extremely engaging, and fun.
            Your response must be returned STRICTLY as a raw JSON object matching the requested schema. 
            Do not include any normal chat text. Only output valid parseable JSON.
        """.trimIndent()

        val prompt = """
            Input Topic or Note Materials:
            "$topicOrNote"

            Target Difficulty: $difficulty
            Is "Exam Tomorrow" (Intensive Review Mode) Active?: $isExamTomorrow

            Please perform these tasks and output the exact JSON structure specified below:
            
            1. Short Summary: Draft a crisp, intuitive summary that explains core principles.
               - If Is Exam Tomorrow is true, make this a high-impact, rapid-fire exam bullet checklist (absolutely vital focus items).
               - Otherwise, make it a smooth conceptual explanation focused on understanding rather than boring rote memory data points.
               
            2. Explain Like I'm 12 (ELI12): Break down the hardest concept in this topic using a funny, highly visual analogy or metaphor (e.g. explaining electricity like water flowing in pipelines). Use psychology to build immediate sticky associations.
            
            3. Mnemonics: Provide list of 2-3 clever mnemonics, abbreviation acrostics, or fun memory hooks.
            
            4. Key Points: List of 4-6 crucial core points.
            
            5. Active Recall Cards: Provide 3 short question and answer self-test pairs format to activate physical retrieval pathways.
            
            6. Quiz: Provide exactly 5 quiz questions. Each question must include:
               - question: Clear testing item
               - options: Exactly 4 distinct multiple choice strings
               - correctOptionIndex: Integer index (0 to 3) representing the correct answer
               - explanation: A helpful psychological breakdown explaining why it's correct to reinforce learning on choice.
               
            Scale questions complexity appropriately to difficulty stage $difficulty.

            JSON Schema Requirement:
            {
               "summary": "String detailing crisp summary",
               "eli12": "String detailing metaphor story",
               "mnemonics": ["Mnemonic 1 string", "Mnemonic 2 string"],
               "keyPoints": ["Point 1", "Point 2", "Point 3", "Point 4"],
               "activeRecall": [
                  { "question": "Question 1?", "answer": "Answer 1" },
                  { "question": "Question 2?", "answer": "Answer 2" }
               ],
               "quiz": [
                  {
                     "question": "Quiz question item 1?",
                     "options": ["A", "B", "C", "D"],
                     "correctOptionIndex": 0,
                     "explanation": "Why correct explanation"
                  }
               ]
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.7f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        val apiResponse = RetrofitClient.service.generateContent(apiKey, request)
        val rawJson = apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("AI returned an empty response.")

        val cleanedJson = cleanJson(rawJson)

        val parsed = try {
            JsonHelper.moshi.adapter(GeminiStudyResponse::class.java).fromJson(cleanedJson)
                ?: throw IllegalArgumentException("Failed to decode JSON data configuration.")
        } catch (e: Exception) {
            throw IllegalStateException("JSON Parse Error: ${e.message}\nRaw text was:\n$cleanedJson", e)
        }

        // Convert parsed properties to JSON strings for Room storage
        val recallList = parsed.activeRecall?.map { ActiveRecallCard(it.question, it.answer) } ?: emptyList()
        val quizQuestionsList = parsed.quiz?.map {
            QuizQuestion(it.question, it.options, it.correctOptionIndex, it.explanation)
        } ?: emptyList()

        val session = StudySession(
            topic = if (topicOrNote.length > 40) topicOrNote.take(37) + "..." else topicOrNote,
            noteContent = topicOrNote,
            summary = parsed.summary,
            eli12 = parsed.eli12,
            mnemonicsJson = JsonHelper.stringListToJson(parsed.mnemonics),
            keyPointsJson = JsonHelper.stringListToJson(parsed.keyPoints),
            activeRecallJson = JsonHelper.activeRecallListToJson(recallList),
            quizJson = JsonHelper.quizListToJson(quizQuestionsList),
            difficulty = difficulty,
            isExamTomorrowMode = isExamTomorrow
        )

        studySessionDao.insertSession(session)

        // Triggers streaks updates
        triggerStreakUpdate()

        // Achievement checks for unlocking badges
        unlockBadgeDirect("first_steps")
        if (isExamTomorrow) {
            unlockBadgeDirect("midnight_oil")
        }

        session
    }

    private fun cleanJson(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json")
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```")
        }
        return cleaned.trim()
    }

    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun getYesterdayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val yesterday = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        return sdf.format(yesterday)
    }
}
