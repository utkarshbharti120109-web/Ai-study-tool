package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Int): StudySession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySession): Long

    @Query("DELETE FROM study_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Int)

    @Query("DELETE FROM study_sessions")
    suspend fun deleteAllSessions()
}

@Dao
interface QuizHistoryDao {
    @Query("SELECT * FROM quiz_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<QuizHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: QuizHistory)

    @Query("SELECT AVG(score) FROM quiz_history")
    fun getAverageScoreFlow(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM quiz_history")
    fun getTotalQuizzesFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM quiz_history WHERE score = 5")
    fun getPerfectQuizzesCount(): Int

    @Query("DELETE FROM quiz_history")
    suspend fun clearHistory()
}

@Dao
interface StudyStreakDao {
    @Query("SELECT * FROM study_streaks WHERE id = 1 LIMIT 1")
    fun getStreakFlow(): Flow<StudyStreak?>

    @Query("SELECT * FROM study_streaks WHERE id = 1 LIMIT 1")
    suspend fun getStreakDirect(): StudyStreak?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreak(streak: StudyStreak)
}

@Dao
interface StudyReminderDao {
    @Query("SELECT * FROM study_reminders ORDER BY hour ASC, minute ASC")
    fun getAllReminders(): Flow<List<StudyReminder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: StudyReminder): Long

    @Update
    suspend fun updateReminder(reminder: StudyReminder)

    @Query("DELETE FROM study_reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Int)
}

@Dao
interface AchievementBadgeDao {
    @Query("SELECT * FROM achievement_badges")
    fun getAllBadges(): Flow<List<AchievementBadge>>

    @Query("SELECT * FROM achievement_badges")
    suspend fun getAllBadgesDirect(): List<AchievementBadge>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadge(badge: AchievementBadge)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadges(badges: List<AchievementBadge>)

    @Query("UPDATE achievement_badges SET isUnlocked = 1, unlockedAt = :unlockedAt WHERE id = :id")
    suspend fun unlockBadge(id: String, unlockedAt: Long)
}
