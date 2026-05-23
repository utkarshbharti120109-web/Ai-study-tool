package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.JsonHelper
import com.example.data.db.QuizQuestion
import com.example.data.db.StudyReminder
import com.example.data.db.StudySession
import com.example.data.repository.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GeneratingUiState {
    object Idle : GeneratingUiState
    object Loading : GeneratingUiState
    data class Success(val session: StudySession) : GeneratingUiState
    data class Error(val message: String) : GeneratingUiState
}

class StudyViewModel(
    application: Application,
    private val repository: StudyRepository
) : AndroidViewModel(application) {

    // Input States
    val topicInput = MutableStateFlow("")
    val selectedDifficulty = MutableStateFlow("MEDIUM") // EASY, MEDIUM, HARD
    val isExamTomorrow = MutableStateFlow(false)

    // Generator UI State
    private val _generatingState = MutableStateFlow<GeneratingUiState>(GeneratingUiState.Idle)
    val generatingState: StateFlow<GeneratingUiState> = _generatingState.asStateFlow()

    // Active Generated Studying Session (when clicking from history or after generation)
    private val _viewingSession = MutableStateFlow<StudySession?>(null)
    val viewingSession: StateFlow<StudySession?> = _viewingSession.asStateFlow()

    // --- Active Quiz State Machine ---
    private val _activeQuizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val activeQuizQuestions: StateFlow<List<QuizQuestion>> = _activeQuizQuestions.asStateFlow()

    val currentQuestionIndex = MutableStateFlow(0)
    val selectedAnswerIndex = MutableStateFlow<Int?>(null)
    val isAnswerSubmitted = MutableStateFlow(false)
    val correctAnswersCount = MutableStateFlow(0)
    val showQuizResults = MutableStateFlow(false)
    val currentQuizTopic = MutableStateFlow("")

    val sessions = repository.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val quizHistory = repository.quizHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val averageScore = repository.averageScore.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val totalQuizzes = repository.totalQuizzes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val currentStreak = repository.currentStreak.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val reminders = repository.allReminders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val badges = repository.allBadges.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.populateDefaultBadges()
        }
    }

    // Generate action
    fun generateStudySession() {
        if (topicInput.value.trim().isEmpty()) {
            _generatingState.value = GeneratingUiState.Error("Please enter notes or a topic first.")
            return
        }

        viewModelScope.launch {
            _generatingState.value = GeneratingUiState.Loading
            try {
                val session = repository.generateStudyGuide(
                    topicOrNote = topicInput.value,
                    difficulty = selectedDifficulty.value,
                    isExamTomorrow = isExamTomorrow.value
                )
                _generatingState.value = GeneratingUiState.Success(session)
                _viewingSession.value = session
                // Clear inputs on success
                topicInput.value = ""
            } catch (e: Exception) {
                _generatingState.value = GeneratingUiState.Error(e.message ?: "An unknown error occurred.")
            }
        }
    }

    fun setViewingSession(session: StudySession?) {
        _viewingSession.value = session
        // Clear active quiz if swapping sessions
        closeQuiz()
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_viewingSession.value?.id == sessionId) {
                _viewingSession.value = null
            }
        }
    }

    fun clearGeneratingError() {
        if (_generatingState.value is GeneratingUiState.Error) {
            _generatingState.value = GeneratingUiState.Idle
        }
    }

    // --- Quiz Actions ---
    fun startQuiz(session: StudySession) {
        val questions = JsonHelper.jsonToQuizList(session.quizJson)
        if (questions.isNotEmpty()) {
            currentQuizTopic.value = session.topic
            _activeQuizQuestions.value = questions
            currentQuestionIndex.value = 0
            selectedAnswerIndex.value = null
            isAnswerSubmitted.value = false
            correctAnswersCount.value = 0
            showQuizResults.value = false
        }
    }

    fun submitAnswer() {
        val selectedIdx = selectedAnswerIndex.value ?: return
        val questions = _activeQuizQuestions.value
        val currentIdx = currentQuestionIndex.value
        val currentQuestion = questions.getOrNull(currentIdx) ?: return

        isAnswerSubmitted.value = true
        if (selectedIdx == currentQuestion.correctOptionIndex) {
            correctAnswersCount.value += 1
        }
    }

    fun nextQuestion() {
        val nextIdx = currentQuestionIndex.value + 1
        if (nextIdx < _activeQuizQuestions.value.size) {
            currentQuestionIndex.value = nextIdx
            selectedAnswerIndex.value = null
            isAnswerSubmitted.value = false
        } else {
            // End of quiz
            saveQuizResult()
            showQuizResults.value = true
        }
    }

    private fun saveQuizResult() {
        viewModelScope.launch {
            repository.saveQuizHistory(
                topic = currentQuizTopic.value,
                score = correctAnswersCount.value,
                totalQuestions = _activeQuizQuestions.value.size,
                difficulty = selectedDifficulty.value
            )
        }
    }

    fun closeQuiz() {
        _activeQuizQuestions.value = emptyList()
        currentQuestionIndex.value = 0
        selectedAnswerIndex.value = null
        isAnswerSubmitted.value = false
        correctAnswersCount.value = 0
        showQuizResults.value = false
    }

    // --- Reminder Actions ---
    fun addReminder(title: String, hour: Int, minute: Int) {
        viewModelScope.launch {
            val reminder = StudyReminder(
                title = title,
                hour = hour,
                minute = minute,
                isEnabled = true
            )
            repository.createReminder(reminder)
        }
    }

    fun toggleReminder(reminder: StudyReminder) {
        viewModelScope.launch {
            repository.updateReminder(reminder.copy(isEnabled = !reminder.isEnabled))
        }
    }

    fun deleteReminder(id: Int) {
        viewModelScope.launch {
            repository.deleteReminder(id)
        }
    }

    fun simulateNotification(context: Context, reminder: StudyReminder) {
        val msg = "📚 Time for ${reminder.title}! Keep up your streak! (Simulated study reminder)"
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    // Trigger active recall complete badge
    fun completeActiveRecallReview() {
        viewModelScope.launch {
            repository.unlockBadgeDirect("recall_champion")
        }
    }

    // Clear progress completely
    fun resetAllAppProgress() {
        viewModelScope.launch {
            repository.clearAllData()
            _generatingState.value = GeneratingUiState.Idle
            _viewingSession.value = null
            closeQuiz()
        }
    }
}

class StudyViewModelFactory(
    private val application: Application,
    private val repository: StudyRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StudyViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
