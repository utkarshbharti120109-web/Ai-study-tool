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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

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
    
    // New premium configuration inputs
    val selectedQuizType = MutableStateFlow("MCQ") // MCQ, Assertion-Reason, Numerical, Short/Long Answers
    val selectedTargetExam = MutableStateFlow("JEE") // EASY, BOARD, JEE, NEET
    
    // Voice Mode States
    val voiceAnswer = MutableStateFlow("")
    val isVoiceLoading = MutableStateFlow(false)
    private var tts: android.speech.tts.TextToSpeech? = null
    @Volatile private var isTtsReady = false
    
    // Pomodoro Timer States
    val focusTimerSecondsLeft = MutableStateFlow(1500) // 25:00 default
    val focusTimerIsRunning = MutableStateFlow(false)
    val focusTimerIsBreak = MutableStateFlow(false)
    private var timerJob: kotlinx.coroutines.Job? = null
    
    // Formula Scanner & Camera Solver States
    val isScannerLoading = MutableStateFlow(false)
    val scannerResult = MutableStateFlow<StudySession?>(null)

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

    val isLoggedIn = MutableStateFlow(false)
    val loggedInEmail = MutableStateFlow("")
    val customApiKey = MutableStateFlow("")
    val hfModelDownloaded = MutableStateFlow(false)
    val hfGemmaDownloaded = MutableStateFlow(false)

    init {
        try {
            val userPrefs = application.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            isLoggedIn.value = userPrefs.getBoolean("is_logged_in", false)
            loggedInEmail.value = userPrefs.getString("logged_in_email", "") ?: ""

            val settingsPrefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            customApiKey.value = settingsPrefs.getString("custom_api_key", "") ?: ""
            hfModelDownloaded.value = settingsPrefs.getBoolean("hf_model_downloaded", false)
            hfGemmaDownloaded.value = settingsPrefs.getBoolean("hf_gemma_downloaded", false)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        viewModelScope.launch {
            repository.populateDefaultBadges()
        }
        viewModelScope.launch(Dispatchers.Main) {
            initTts(application)
        }
    }

    fun setLoginStatus(email: String, loggedIn: Boolean) {
        try {
            val userPrefs = getApplication<Application>().getSharedPreferences("user_session", Context.MODE_PRIVATE)
            userPrefs.edit()
                .putBoolean("is_logged_in", loggedIn)
                .putString("logged_in_email", email)
                .apply()
            isLoggedIn.value = loggedIn
            loggedInEmail.value = email
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveCustomApiKey(key: String) {
        try {
            val settingsPrefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            settingsPrefs.edit()
                .putString("custom_api_key", key)
                .apply()
            customApiKey.value = key
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setModelDownloadState(modelName: String, downloaded: Boolean) {
        try {
            val settingsPrefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            if (modelName == "TinyLlama") {
                settingsPrefs.edit().putBoolean("hf_model_downloaded", downloaded).apply()
                hfModelDownloaded.value = downloaded
            } else if (modelName == "Gemma") {
                settingsPrefs.edit().putBoolean("hf_gemma_downloaded", downloaded).apply()
                hfGemmaDownloaded.value = downloaded
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Voice Teacher Actions
    fun initTts(context: Context) {
        if (tts == null) {
            synchronized(this) {
                if (tts == null) {
                    try {
                        isTtsReady = false
                        val appCtx = context.applicationContext
                        tts = android.speech.tts.TextToSpeech(appCtx) { status ->
                            try {
                                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                                    val result = tts?.setLanguage(java.util.Locale.US)
                                    if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                                        result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                                        isTtsReady = false
                                    } else {
                                        isTtsReady = true
                                    }
                                } else {
                                    isTtsReady = false
                                }
                            } catch (e: java.lang.Exception) {
                                e.printStackTrace()
                                isTtsReady = false
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        isTtsReady = false
                    }
                }
            }
        }
    }

    fun speakText(text: String, context: Context) {
        if (text.isEmpty()) return
        viewModelScope.launch {
            try {
                initTts(context)
                var retries = 0
                while (!isTtsReady && retries < 15) {
                    delay(200)
                    retries++
                }
                if (isTtsReady && tts != null) {
                    tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "StudyBuddyVoiceText")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun queryVoiceTeacher(query: String, context: Context) {
        if (query.trim().isEmpty()) return
        voiceAnswer.value = ""
        isVoiceLoading.value = true
        initTts(context)
        
        viewModelScope.launch {
            try {
                val clKey = customApiKey.value
                val apiKey = if (clKey.isNotEmpty()) clKey else com.example.BuildConfig.GEMINI_API_KEY
                val responseText = if (apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
                    val prompt = "Give a concise, hands-free audio explanation of: '$query' for a student. Keep it engaging, under 3 sentences."
                    val req = com.example.data.api.GenerateContentRequest(
                        contents = listOf(com.example.data.api.Content(parts = listOf(com.example.data.api.Part(text = prompt)))),
                        generationConfig = com.example.data.api.GenerationConfig(temperature = 0.6f)
                    )
                    val apiRes = com.example.data.api.RetrofitClient.service.generateContent(apiKey, req)
                    apiRes.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                        ?: "I'm sorry, I couldn't process that query."
                } else {
                    "Offline Voice mode active. For '$query': remember that energy cannot be created or destroyed, it only transforms from work to heat. Keep reviewing flashcards to lock in this concept!"
                }
                
                voiceAnswer.value = responseText
                isVoiceLoading.value = false
                speakText(responseText, context)
                repository.incrementXp(15)
            } catch (e: Exception) {
                e.printStackTrace()
                val hasApiKey = customApiKey.value.isNotEmpty() || (com.example.BuildConfig.GEMINI_API_KEY.isNotEmpty() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY")
                val errMsg = e.localizedMessage ?: "HTTP 403 Forbidden"
                val errorNotice = if (!hasApiKey) {
                    "Voice mode error. No valid API key configured. Please enter your personal Gemini API Key in the Settings panel!"
                } else {
                    "Offline voice mode fallback (API returned: $errMsg). For '$query', remember that physics and chemistry laws dictate how materials behave. Make sure to review your guidebook!"
                }
                voiceAnswer.value = errorNotice
                isVoiceLoading.value = false
                speakText(errorNotice, context)
            }
        }
    }

    // Pomodoro Timer Actions
    fun startFocusTimer() {
        if (focusTimerIsRunning.value) return
        focusTimerIsRunning.value = true
        timerJob = viewModelScope.launch {
            while (focusTimerSecondsLeft.value > 0) {
                kotlinx.coroutines.delay(1000)
                focusTimerSecondsLeft.value -= 1
            }
            focusTimerIsRunning.value = false
            if (focusTimerIsBreak.value) {
                focusTimerIsBreak.value = false
                focusTimerSecondsLeft.value = 1500
            } else {
                repository.incrementXp(120) // Mega focus reward!
                focusTimerIsBreak.value = true
                focusTimerSecondsLeft.value = 300 // 5:00 break
            }
        }
    }

    fun pauseFocusTimer() {
        focusTimerIsRunning.value = false
        timerJob?.cancel()
    }

    fun resetFocusTimer() {
        pauseFocusTimer()
        focusTimerIsBreak.value = false
        focusTimerSecondsLeft.value = 1500
    }

    // Scanner / Doubt Camera Actions
    fun simulatePhotoOcrAndSolve(context: Context) {
        if (isScannerLoading.value) return
        isScannerLoading.value = true
        scannerResult.value = null
        
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000) // Simulate image capture OCR processing delay
            try {
                // Returns step-by-step math solve
                val scanTopic = "Calculus limits: lim x->0 (sin x)/x"
                val clKey = customApiKey.value
                val sessionResult = repository.generateStudyGuide(
                    topicOrNote = "Solve the calculus limit limit x tends to 0 of sine of x divided by x step by step using standard Taylor expansion or L'Hopital rule.",
                    difficulty = "MEDIUM",
                    isExamTomorrow = false,
                    quizType = "Numerical",
                    targetExam = "JEE",
                    apiRouterModel = "OCR",
                    localModelEnabledFlag = false,
                    customApiKey = clKey
                )
                scannerResult.value = sessionResult
                isScannerLoading.value = false
                repository.incrementXp(40) // Grant OCR solve points
                Toast.makeText(context, "Formula Scanned & Solved!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                isScannerLoading.value = false
                Toast.makeText(context, "OCR scanner failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun selectSettingsExamType(exam: String) {
        viewModelScope.launch {
            repository.updateTargetExam(exam)
        }
    }

    fun toggleOfflineSetting(enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleLocalModel(enabled)
        }
    }

    fun updateSettingsRouterMode(model: String) {
        viewModelScope.launch {
            repository.updateRouterModel(model)
        }
    }

    // Active Recall / Flashcard forgot-mastered actions
    fun markFlashcardAsForgotten(question: String, topic: String) {
        viewModelScope.launch {
            repository.trackWeakChapterAndForgotCard(topic, question)
        }
    }

    fun markFlashcardAsMastered(question: String) {
        viewModelScope.launch {
            repository.markCardAsMastered(question)
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
                val currentStreakInfo = currentStreak.value ?: com.example.data.db.StudyStreak(id = 1)
                val clKey = customApiKey.value
                val session = repository.generateStudyGuide(
                    topicOrNote = topicInput.value,
                    difficulty = selectedDifficulty.value,
                    isExamTomorrow = isExamTomorrow.value,
                    quizType = selectedQuizType.value,
                    targetExam = currentStreakInfo.targetExam,
                    apiRouterModel = currentStreakInfo.apiRouterModel,
                    localModelEnabledFlag = currentStreakInfo.localModelEnabled,
                    customApiKey = clKey
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

    override fun onCleared() {
        super.onCleared()
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            e.printStackTrace()
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
