package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// --- Simple JSON structures for nested properties ---

data class ActiveRecallCard(
    val question: String,
    val answer: String
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String
)

// --- Room Entities ---

@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val topic: String,
    val noteContent: String,
    val summary: String,
    val eli12: String,
    val mnemonicsJson: String,     // List<String>
    val keyPointsJson: String,     // List<String>
    val activeRecallJson: String,  // List<ActiveRecallCard>
    val quizJson: String,          // List<QuizQuestion>
    val difficulty: String,        // EASY, MEDIUM, HARD
    val isExamTomorrowMode: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_history")
data class QuizHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val topic: String,
    val score: Int,
    val totalQuestions: Int = 5,
    val difficulty: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_streaks")
data class StudyStreak(
    @PrimaryKey val id: Int = 1,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val lastStudyDate: String = "" // YYYY-MM-DD
)

@Entity(tableName = "study_reminders")
data class StudyReminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true
)

@Entity(tableName = "achievement_badges")
data class AchievementBadge(
    @PrimaryKey val id: String, // e.g., "first_steps", "perfect_quiz", "streak_3", "midnight_oil", "master_mind"
    val title: String,
    val description: String,
    val iconName: String,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null
)

// --- Single Moshi JSON Helper for Room Type Converters and API parsing ---

object JsonHelper {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun stringListToJson(list: List<String>): String {
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        return moshi.adapter<List<String>>(type).toJson(list)
    }

    fun jsonToStringList(json: String): List<String> {
        return try {
            val type = Types.newParameterizedType(List::class.java, String::class.java)
            moshi.adapter<List<String>>(type).fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun activeRecallListToJson(list: List<ActiveRecallCard>): String {
        val type = Types.newParameterizedType(List::class.java, ActiveRecallCard::class.java)
        return moshi.adapter<List<ActiveRecallCard>>(type).toJson(list)
    }

    fun jsonToActiveRecallList(json: String): List<ActiveRecallCard> {
        return try {
            val type = Types.newParameterizedType(List::class.java, ActiveRecallCard::class.java)
            moshi.adapter<List<ActiveRecallCard>>(type).fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun quizListToJson(list: List<QuizQuestion>): String {
        val type = Types.newParameterizedType(List::class.java, QuizQuestion::class.java)
        return moshi.adapter<List<QuizQuestion>>(type).toJson(list)
    }

    fun jsonToQuizList(json: String): List<QuizQuestion> {
        return try {
            val type = Types.newParameterizedType(List::class.java, QuizQuestion::class.java)
            moshi.adapter<List<QuizQuestion>>(type).fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
