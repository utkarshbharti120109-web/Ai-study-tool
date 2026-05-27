package com.example.data.repository

import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.api.HuggingFaceRetrofitClient
import com.example.data.api.HuggingFaceSearchResponse
import com.example.data.api.HuggingFaceRow
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
    val quiz: List<QuizQuestionJson>?,
    
    // New premium returned elements
    val formulas: List<String>?,
    val examQuestions: List<String>?,
    val eli11: String?,
    val derivations: List<String>?,
    val commonMistakes: List<String>?,
    val predictedTopics: List<String>?,
    val priorityScore: Int?
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

    // Gamification & Level progression helpers
    suspend fun incrementXp(amount: Int) = withContext(Dispatchers.IO) {
        val streak = studyStreakDao.getStreakDirect() ?: StudyStreak(id = 1)
        var newXp = streak.xp + amount
        var newLevel = streak.level
        
        // Let level progress by e.g. 150 * Level XP per level
        val threshold = newLevel * 120
        if (newXp >= threshold) {
            newXp -= threshold
            newLevel += 1
        }
        val pct = (newXp.toFloat() / (newLevel * 120)).coerceIn(0f, 1f)
        
        studyStreakDao.insertStreak(streak.copy(
            xp = newXp,
            level = newLevel,
            plantProgress = pct
        ))
    }

    suspend fun toggleLocalModel(enabled: Boolean) = withContext(Dispatchers.IO) {
        val streak = studyStreakDao.getStreakDirect() ?: StudyStreak(id = 1)
        studyStreakDao.insertStreak(streak.copy(localModelEnabled = enabled))
    }

    suspend fun updateTargetExam(exam: String) = withContext(Dispatchers.IO) {
        val streak = studyStreakDao.getStreakDirect() ?: StudyStreak(id = 1)
        studyStreakDao.insertStreak(streak.copy(targetExam = exam))
    }

    suspend fun updateRouterModel(model: String) = withContext(Dispatchers.IO) {
        val streak = studyStreakDao.getStreakDirect() ?: StudyStreak(id = 1)
        studyStreakDao.insertStreak(streak.copy(apiRouterModel = model))
    }

    suspend fun trackWeakChapterAndForgotCard(topic: String, question: String) = withContext(Dispatchers.IO) {
        val streak = studyStreakDao.getStreakDirect() ?: StudyStreak(id = 1)
        
        val currentWeak = try {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
            JsonHelper.moshi.adapter<List<String>>(type).fromJson(streak.weakChaptersJson) ?: listOf()
        } catch (e: Exception) { listOf() }
        
        val updatedWeak = if (!currentWeak.contains(topic)) currentWeak + topic else currentWeak
        val weakJson = JsonHelper.stringListToJson(updatedWeak)

        val currentForgot = try {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
            JsonHelper.moshi.adapter<List<String>>(type).fromJson(streak.forgottenCardsJson) ?: listOf()
        } catch (e: Exception) { listOf() }
        
        val updatedForgot = if (!currentForgot.contains(question)) currentForgot + question else currentForgot
        val forgotJson = JsonHelper.stringListToJson(updatedForgot)

        studyStreakDao.insertStreak(streak.copy(
            weakChaptersJson = weakJson,
            forgottenCardsJson = forgotJson
        ))
        incrementXp(5)
    }

    suspend fun markCardAsMastered(question: String) = withContext(Dispatchers.IO) {
        val streak = studyStreakDao.getStreakDirect() ?: StudyStreak(id = 1)
        
        val currentForgot = try {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
            JsonHelper.moshi.adapter<List<String>>(type).fromJson(streak.forgottenCardsJson) ?: listOf()
        } catch (e: Exception) { listOf() }
        
        val updatedForgot = currentForgot.filter { it != question }
        val forgotJson = JsonHelper.stringListToJson(updatedForgot)

        studyStreakDao.insertStreak(streak.copy(
            forgottenCardsJson = forgotJson
        ))
        incrementXp(25)
    }

    suspend fun triggerStreakUpdate() = withContext(Dispatchers.IO) {
        val today = getCurrentDateString()
        val yesterday = getYesterdayDateString()
        val currentStreakRecord = studyStreakDao.getStreakDirect() ?: StudyStreak(id = 1)

        val updatedStreak = when (currentStreakRecord.lastStudyDate) {
            today -> {
                currentStreakRecord
            }
            yesterday -> {
                val newCurrent = currentStreakRecord.currentStreak + 1
                val newMax = if (newCurrent > currentStreakRecord.maxStreak) newCurrent else currentStreakRecord.maxStreak
                currentStreakRecord.copy(currentStreak = newCurrent, maxStreak = newMax, lastStudyDate = today)
            }
            else -> {
                val newCurrent = 1
                val newMax = if (newCurrent > currentStreakRecord.maxStreak) newCurrent else currentStreakRecord.maxStreak
                currentStreakRecord.copy(currentStreak = newCurrent, maxStreak = newMax, lastStudyDate = today)
            }
        }
        studyStreakDao.insertStreak(updatedStreak)

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
        triggerStreakUpdate()
        incrementXp(score * 15) // Grant XP based on correct quiz answers!

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
        studyStreakDao.insertStreak(StudyStreak(id = 1, currentStreak = 0, maxStreak = 0, lastStudyDate = "", xp = 0, level = 1, plantProgress = 0f))
    }

    suspend fun unlockBadgeDirect(badgeId: String) = withContext(Dispatchers.IO) {
        achievementBadgeDao.unlockBadge(badgeId, System.currentTimeMillis())
    }

    suspend fun generateStudyGuide(
        topicOrNote: String,
        difficulty: String,
        isExamTomorrow: Boolean,
        quizType: String = "MCQ", // MCQ, Assertion-Reason, Numerical, Short/Long Answers
        targetExam: String = "JEE", // Easy, Board, JEE, NEET
        apiRouterModel: String = "AUTO",
        localModelEnabledFlag: Boolean = false,
        customApiKey: String = "",
        warningNotice: String = ""
    ): StudySession = withContext(Dispatchers.IO) {
        val apiKey = if (customApiKey.isNotEmpty()) customApiKey else BuildConfig.GEMINI_API_KEY
        val cleanTopic = if (topicOrNote.length > 40) topicOrNote.take(37) + "..." else topicOrNote
        
        // Check if offline mode is explicitly enabled, or if API key is not configured
        if (localModelEnabledFlag || apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            var hfQuiz: List<QuizQuestion>? = null
            var warningAttached = warningNotice

            // High-quality offline simulated extraction for CBSE physics/chemistry/math / TinyLlama
            val topicLower = topicOrNote.lowercase()
            val category = when {
                topicLower.contains("thermo") || topicLower.contains("heat") || topicLower.contains("entropy") || topicLower.contains("carnot") || topicLower.contains("gas") -> "Thermodynamics"
                topicLower.contains("integrat") || topicLower.contains("calculus") || topicLower.contains("differentiat") || topicLower.contains("limit") || topicLower.contains("math") || topicLower.contains("deriv") || topicLower.contains("vector") || topicLower.contains("matrix") || topicLower.contains("set") || topicLower.contains("algebra") -> "Mathematics"
                topicLower.contains("organic") || topicLower.contains("chem") || topicLower.contains("carbon") || topicLower.contains("reaction") || topicLower.contains("acid") || topicLower.contains("bond") || topicLower.contains("atom") || topicLower.contains("periodic") || topicLower.contains("molecule") || topicLower.contains("compound") || topicLower.contains("element") -> "Chemistry"
                topicLower.contains("electro") || topicLower.contains("magnet") || topicLower.contains("current") || topicLower.contains("charge") || topicLower.contains("potential") || topicLower.contains("capacitor") || topicLower.contains("resistance") || topicLower.contains("volt") -> "Electromagnetism"
                topicLower.contains("optics") || topicLower.contains("light") || topicLower.contains("wave") || topicLower.contains("ray") || topicLower.contains("lens") || topicLower.contains("mirror") || topicLower.contains("quantum") || topicLower.contains("photoelectric") -> "Optics & Wave Physics"
                topicLower.contains("motion") || topicLower.contains("force") || topicLower.contains("gravit") || topicLower.contains("kinematic") || topicLower.contains("mechanic") || topicLower.contains("speed") || topicLower.contains("velocity") || topicLower.contains("acceleration") || topicLower.contains("friction") || topicLower.contains("work") || topicLower.contains("energy") || topicLower.contains("power") || topicLower.contains("torque") || topicLower.contains("rotational") -> "Mechanics & Kinematics"
                else -> "General Science"
            }

            if (category == "General Science" || topicOrNote.length > 3) {
                try {
                    val collectedRows = mutableListOf<HuggingFaceRow>()
                    val seenQuestions = mutableSetOf<String>()

                    // First Attempt: Full topic query
                    try {
                        val response = HuggingFaceRetrofitClient.service.searchDataset(query = topicOrNote)
                        for (item in response.rows) {
                            val r = item.row
                            val normQ = r.question.trim().lowercase()
                            if (r.question.isNotEmpty() && r.correct_answer.isNotEmpty() && !seenQuestions.contains(normQ)) {
                                seenQuestions.add(normQ)
                                collectedRows.add(r)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Second Attempt: If we got fewer than 5 rows, query with helper keyword matching the domain category
                    if (collectedRows.size < 5) {
                        val secondaryQuery = when (category) {
                            "Thermodynamics" -> "thermodynamics"
                            "Mathematics" -> "calculus"
                            "Chemistry" -> "chemistry"
                            "Electromagnetism" -> "electricity"
                            "Optics & Wave Physics" -> "optics"
                            "Mechanics & Kinematics" -> "mechanics"
                            else -> "science"
                        }
                        try {
                            val response = HuggingFaceRetrofitClient.service.searchDataset(query = secondaryQuery)
                            for (item in response.rows) {
                                val r = item.row
                                val normQ = r.question.trim().lowercase()
                                if (r.question.isNotEmpty() && r.correct_answer.isNotEmpty() && !seenQuestions.contains(normQ)) {
                                    seenQuestions.add(normQ)
                                    collectedRows.add(r)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (collectedRows.isNotEmpty()) {
                        hfQuiz = collectedRows.take(5).map { row ->
                            val options = mutableListOf(row.correct_answer, row.distractor1)
                            if (!row.distractor2.isNullOrEmpty()) options.add(row.distractor2)
                            if (!row.distractor3.isNullOrEmpty()) options.add(row.distractor3)
                            options.shuffle()
                            val correctIndex = options.indexOf(row.correct_answer).coerceAtLeast(0)
                            QuizQuestion(
                                question = row.question,
                                options = options,
                                correctOptionIndex = correctIndex,
                                explanation = row.support ?: "Downloaded live from Hugging Face SciQ dataset repository for a personalized study challenge."
                            )
                        }
                        warningAttached = if (warningAttached.isNotEmpty()) "[Hugging Face DB Loaded]\n$warningAttached" else "[Hugging Face DB Loaded] "
                    }
                } catch (e: Exception) {
                    // Fail silently, use local templates
                }
            }

            // Fill properties based on the selected domain category
            val initialSummary = when (category) {
                "Thermodynamics" -> "Offline Local Intelligence Active ($apiRouterModel):\nComprehensive study pack for $cleanTopic. Exploring thermal equilibrium, state variables, heat flow equations, and entropy trends in closed systems suitable for $targetExam ($difficulty)."
                "Mathematics" -> "Offline Local Intelligence Active ($apiRouterModel):\nComprehensive math pack for $cleanTopic. Covers limits, functional constraints, differential bounds, and integration methodologies optimized for $targetExam ($difficulty)."
                "Chemistry" -> "Offline Local Intelligence Active ($apiRouterModel):\nComprehensive chemistry guide for $cleanTopic. Outlines structural configurations, valence electrons, orbital behaviors, and reaction equilibria for $targetExam ($difficulty)."
                "Electromagnetism" -> "Offline Local Intelligence Active ($apiRouterModel):\nComprehensive electromagnetism guide for $cleanTopic. Analyzes electric fields, charge distributions, magnetic boundaries, and inductive loads for $targetExam ($difficulty)."
                "Optics & Wave Physics" -> "Offline Local Intelligence Active ($apiRouterModel):\nComprehensive wave guide for $cleanTopic. Highlights wave progression, ray trace parameters, optical lenses, and quantum transitions for $targetExam ($difficulty)."
                "Mechanics & Kinematics" -> "Offline Local Intelligence Active ($apiRouterModel):\nComprehensive kinetics pack for $cleanTopic. Models dynamic motion vectors, forces, rotational torque, and gravitation bounds for $targetExam ($difficulty)."
                else -> "Offline Local Intelligence Active ($apiRouterModel):\nInteractive study manual for $cleanTopic. Outlines foundational principles, key qualitative traits, and exam-focused insights for $targetExam ($difficulty)."
            }
            val summaryText = if (warningAttached.isNotEmpty()) warningAttached + initialSummary else initialSummary

            val eli12Text = when (category) {
                "Thermodynamics" -> "Analog Story: Imagine '$cleanTopic' is like a highly organized train station where particles are passengers, temperature is how fast everyone is running around, and entropy is the sudden announcement of a major delay that causes passengers to scatter in random directions!"
                "Mathematics" -> "Analog Story: Imagine '$cleanTopic' is like cutting a large block of cheese into infinitely thin slices so that you can measure every tiny curve of it perfectly, and then stacking them back up to find the total volume of the block!"
                "Chemistry" -> "Analog Story: Imagine '$cleanTopic' is like playing with Lego blocks where atoms are the elements, carbon is the super-connector block with four hands, and chemical bonds are tight arm-locks holding them together!"
                "Electromagnetism" -> "Analog Story: Imagine '$cleanTopic' is like a massive slide in an amusement park. The battery is the ladder lifting kids (charges) to the top, the electric potential is their height, and electrical resistance represents kids bumping into bumper cushions on details of the slide!"
                "Optics & Wave Physics" -> "Analog Story: Imagine '$cleanTopic' is like ripples traveling on a quiet pond when you throw a pebble. The waves slide over each other, bend around lily pads (diffraction), and combine to create beautiful overlapping patterns!"
                "Mechanics & Kinematics" -> "Analog Story: Imagine '$cleanTopic' is like riding a roller coaster! Gravity pulls you down, momentum keeps you speeding forward around the loop-the-loop, and centripetal force is the seat barrier preventing you from flying off the edge!"
                else -> "Analog Story: Imagine '$cleanTopic' is like a puzzle where simple blocks join to support a huge bridge. Every block represents a core physics or chemistry parameter, and understanding how they interlock keeps the whole bridge standing!"
            }

            val mnemonics = when (category) {
                "Thermodynamics" -> listOf("S-T-P (State variables: Size/Volume, Temperature, and Pressure constants)", "H-U-G-S (Heat, Internal Energy, Gibbs Free Energy, Entropy factors)")
                "Mathematics" -> listOf("I-L-A-T-E (Inverse, Logarithmic, Algebraic, Trigonometric, Exponential selection order)", "L-H-S (Limit exists if LHS matches RHS bounds at the boundary)")
                "Chemistry" -> listOf("O-I-L R-I-G (Oxidation Is Loss, Reduction Is Gain of electron packages)", "P-O-N-A (Positive is Anode, Negative is Cathode in electrolytic setups)")
                "Electromagnetism" -> listOf("V-I-R (Voltage = Current * Resistance Ohm relation)", "L-E-N-Z (Lenz law opposes change in magnetic flux flows)")
                "Optics & Wave Physics" -> listOf("R-O-Y G B-I-V (Red, Orange, Yellow, Green, Blue, Indigo, Violet wave dispersion order)", "S-N-E-L-L (Snell's index matches sin ratio boundaries)")
                "Mechanics & Kinematics" -> listOf("F-M-A (Force = Mass * Acceleration vector relation)", "P-E-K-E (Potential Energy and Kinetic Energy conservation)")
                else -> listOf("A-C-T-I-V-E (Always Challenge Theoretical Ideas Via Experiments)", "R-E-C-A-L-L (Retrieve Energy Concepts And Learn Long-term)")
            }

            val keyPoints = when (category) {
                "Thermodynamics" -> listOf(
                    "Conservative bounds state that heat energy transferred converts directly to internal energy change and system work.",
                    "Entropy represents the mathematical probability of disorder configurations in isolated systems.",
                    "Thermal equilibrium implies body temperatures have equalized, satisfying the Zeroth Law standards.",
                    "High test weightage in CBSE and JEE exams; pays to avoid common sign convention errors."
                )
                "Mathematics" -> listOf(
                    "Linear limits monitor functional boundaries as variables approach specific points of interest.",
                    "Definite integration represents the exact signed area locked under the mathematical curve.",
                    "Continuity is a strict mathematical prerequisite for a function to accept derivatives.",
                    "High weighting on board exams; practice sketching curves to visualize integration boundaries."
                )
                "Chemistry" -> listOf(
                    "Covalent bonds share valence electron clouds to reach stable shell configurations.",
                    "Resonance delocalizes pi electron pools across secondary atoms, driving down net molecules energy.",
                    "Hybridization blends s and p atomic orbitals to create symmetrical geometric bonding sites.",
                    "High test value for JEE organic chemistry; focus on negative nucleophile attack steps."
                )
                "Electromagnetism" -> listOf(
                    "Gauss's law links electric flux density to enclosed charge quantities.",
                    "Capacitance scales electrical charge storage per unit potential difference.",
                    "Magnetic force is perpendicular to both particle charge velocity and magnetic field vectors.",
                    "A core conceptual target on all physical entrance exams."
                )
                "Optics & Wave Physics" -> listOf(
                    "Snell's relationship bounds wave bending at material density interfaces.",
                    "Diffraction scatters waves when colliding with apertures of similar scale.",
                    "Wave frequency is governed by the source and does not shift upon passing through lenses.",
                    "High JEE/NEET focus on wave interference and optical refractive index formulas."
                )
                "Mechanics & Kinematics" -> listOf(
                    "Displacement represents shortest vector distance separate from total path traveled.",
                    "Newtonian forces represent rate of change of linear momentum over time interval.",
                    "Conservation of momentum prevails in all isolated collisions regardless of elasticity.",
                    "Standard foundation topic in Grade 11 CBSE syllabus."
                )
                else -> listOf(
                    "Foundational parameters govern physical system parameters under changing boundaries.",
                    "Formula accuracy rests heavily on proper dimensional units translation.",
                    "Dynamic review maps help bypass rote memorization in secondary classes.",
                    "Always focus on analyzing graphs to understand rapid test questions."
                )
            }

            val formulas = when (category) {
                "Thermodynamics" -> listOf("dQ = dU + dW (First Law equivalent)", "PV = nRT (Universal ideal gas relation)", "Efficiency (η) = 1 - (T_cold / T_hot)")
                "Mathematics" -> listOf("∫ x^n dx = (x^(n+1))/(n+1) + C", "d/dx [f(x)/g(x)] = (f'g - fg') / g²", "a · b = |a||b| cos(θ) (Dot product)")
                "Chemistry" -> listOf("pH = -log[H+] (Acidity standard)", "ΔG = ΔH - TΔS (Gibbs spontaneity equation)", "Kc = [Products] / [Reactants] at equilibrium")
                "Electromagnetism" -> listOf("V = I * R (Ohm's voltage relation)", "F = q(E + v x B) (Lorentz force equation)", "C = ε0 * A / d (Parallel plate capacitance)")
                "Optics & Wave Physics" -> listOf("n1 sin(θ1) = n2 sin(θ2) (Snell's refraction)", "1 / f = 1 / v + 1 / u (Mirror formula relation)", "E = h * f (Planck's photon energy equivalent)")
                "Mechanics & Kinematics" -> listOf("v = u + at (Velocity change)", "F = m * a (Newton's second law vector relation)", "KE = 0.5 * m * v² (Kinetic motion energy)")
                else -> listOf("Y = f(x) (System output variable)", "Rate = ΔAmount / ΔTime (Process kinetics)", "Constant = Value * Units (Dimensional integrity)")
            }

            val derivations = when (category) {
                "Thermodynamics" -> listOf("Adiabatic relation constraint: PV^γ = Constants", "Work done in isothermal expansion: W = nRT ln(V2 / V1)")
                "Mathematics" -> listOf("Derive integration by parts formula from product rule of derivatives", "Derive coordinate vector projections on coordinate planes")
                "Chemistry" -> listOf("Derive Henderson-Hasselbalch equation for pH buffer mixtures", "Derive relation between gaseous equilibrium metrics Kc and Kp")
                "Electromagnetism" -> listOf("Derive capacitance equation for parallel plate vacuum capacitors", "Derive magnetic field inside long current-carrying solenoids using Ampere")
                "Optics & Wave Physics" -> listOf("Derive lens maker's formula using double boundary spherical refraction rules", "Derive fringe width expression in Young's Double Slit experiment")
                "Mechanics & Kinematics" -> listOf("Derive rotational kinetic energy: KE_rot = 0.5 * I * ω²", "Derive trajectory equation of projectile motion showing parabolic curves")
                else -> listOf("Derive dimensional equations using primary base quantities", "Derive instantaneous velocity vector relations via calculus limits")
            }

            val mistakes = when (category) {
                "Thermodynamics" -> listOf("Confusing positive heat input with negative work done by gas systems in different textbooks.", "Forgetting to absolute-convert Celsius values inside Carnot temperature ratios.")
                "Mathematics" -> listOf("Forgetting to add constant term '+ C' in high school indefinite integrals.", "Assuming limits that yield division by zero are undefined instead of trying factoring.")
                "Chemistry" -> listOf("Routing reaction mechanism arrows from positive nuclei instead of rich electron pairs.", "Confusing ionization enthalpy trends with atomic size changes on periodic periods.")
                "Electromagnetism" -> listOf("Confusing parallel resistor formulas with parallel capacitor additions.", "Misapplying Fleming's right-hand vector rule for electron drifts.")
                "Optics & Wave Physics" -> listOf("Confusing focal length sign guidelines for diverging vs converging lenses.", "Forgetting light frequency remains unchanged when traversing variable materials.")
                "Mechanics & Kinematics" -> listOf("Forgetting to designate uniform directional coordinate axes (+/-) in motion vectors.", "Confusing static friction coefficients with active kinetic sliding bounds.")
                else -> listOf("Using incompatible physical units inside dynamic equations.", "Failing to check boundary criteria constraints in complex formulas.")
            }

            val predictions = when (category) {
                "Thermodynamics" -> listOf("Carnot cycle efficiency comparisons", "Entropy changes in spontaneous gas mix")
                "Mathematics" -> listOf("Area bound evaluation on piecewise trigonometric fields", "Limit evaluation using L'Hopital rules")
                "Chemistry" -> listOf("Prediction of aromatic molecules based on Huckel criteria", "Equilibrium shifts on reactant additions")
                "Electromagnetism" -> listOf("Capacitor charging current curves in RC networks", "Magnetic field of coaxial cable streams")
                "Optics & Wave Physics" -> listOf("Refractive deflection in prism systems", "Interference minimum coordinate shifts")
                "Mechanics & Kinematics" -> listOf("Projectile range optimization on angled paths", "Collision velocities in elastic masses")
                else -> listOf("Graphical interpretation questions on speed ratios", "Dimensional sanity checks on multi-variable metrics")
            }

            val cli11Text = when (category) {
                "Thermodynamics" -> "Simplified Level 11 Summary: Think of this topic like inflating a bicycle tire! The pump heats up because the mechanical work you do gets converted directly into thermal movement of the trapped gas molecules."
                "Mathematics" -> "Simplified Level 11 Summary: Imagine zooming in on a curved road. If you zoom in close enough, the curve looks like a straight line! Calculus is the study of that straight line at a scale of extremely tiny slides."
                "Chemistry" -> "Simplified Level 11 Summary: Think of chemistry like magnets. Opposites attract, pulling positive nuclei and negative electron clouds into perfect shapes that fill up chemistry shells for maximum relaxation!"
                "Electromagnetism" -> "Simplified Level 11 Summary: Think of electrical current like water flowing in pipelines. Voltage is the water pressure pushing everything forward, and resistance is narrow valves blocking the flow."
                "Optics & Wave Physics" -> "Simplified Level 11 Summary: Think of light like wave tracks traveling in space. When light hits glass, it slows down like car tires hitting mud, causing the path to bend!"
                "Mechanics & Kinematics" -> "Simplified Level 11 Summary: Think of motion like playing slide games. Friction tries to drag you to a stop, inertia keeps you sliding when you want to stop, and mass controls how hard it is to push you!"
                else -> "Simplified Level 11 Summary: Think of this topic like structured puzzle-solving. When you break complex notes down into small parts with custom units, it fits perfectly together for study tests!"
            }

            val rawExam = when (category) {
                "Thermodynamics" -> listOf("State and explain the First Law of Thermodynamics along with limitations.", "An ideal gas operates in a Carnot cycle. Derive expression for thermal efficiency.")
                "Mathematics" -> listOf("Evaluate the definite integral ∫ on [0, π/2] of ln(sin x) dx.", "Prove that every differentiable function must be continuous, illustrating with an counterexample.")
                "Chemistry" -> listOf("Distinguish between SN1 and SN2 organic nucleophilic substitution pathways with stereochemical results.", "State Le Chatelier's Principle and discuss influence of temperature and pressure on sulfur trioxide synthesis.")
                "Electromagnetism" -> listOf("State Gauss's Law in electrostatics and use it to derive electric field near infinitely straight charged wire.", "Derive expression for energy density stored in an active parallel-plate vacuum capacitor.")
                "Optics & Wave Physics" -> listOf("Derive lens maker's formula step by step for double convex thin glass lenses.", "Discuss Young's Double Slit experiment and derive formula for linear fringe width on testing screens.")
                "Mechanics & Kinematics" -> listOf("State the Work-Energy Theorem and prove it mathematically for variable forces.", "Derive equation of rotational coordinates showing relation between torque and moment of inertia.")
                else -> listOf("Explain primary base SI units and detail rules of dimensional derivation in chemistry/physics.", "Discuss qualitative indicators and visual metrics with graphical interpretation examples.")
            }

            val activeRecall = when (category) {
                "Thermodynamics" -> listOf(
                    ActiveRecallCard("What is the primary thermodynamic property established by the Zeroth Law?", "Absolute temperature, which governs thermal equilibrium pathways."),
                    ActiveRecallCard("Why does temperature remain constant in isothermal gas expansion?", "Because external key thermal flows continuously compensate for energy converted into sliding mechanical work."),
                    ActiveRecallCard("State Carnot's theorem limits on heat cycles.", "No engine can be more efficient than a perfectly reversible engine operating between identical thermal terminals.")
                )
                "Mathematics" -> listOf(
                    ActiveRecallCard("What is the core geometric meaning of def-integrals?", "The signed accumulated net area enclosed between functional curves and boundary coordinate axes."),
                    ActiveRecallCard("When does a mathematical limit fail to resolve?", "When left-hand bounds and right-hand bounds diverge, or when the value scales to unbound infinity."),
                    ActiveRecallCard("Why are continuous functions not always differentiable?", "Sharp corners or cusps in curves yield non-unique slope indicators, blocking derivative solutions.")
                )
                "Chemistry" -> listOf(
                    ActiveRecallCard("What makes Huckel's rules critical for ring stabilities?", "Cyclic systems require (4n + 2) pi electrons to create fully continuous bonding configurations."),
                    ActiveRecallCard("How does hybridization modify carbon's active valency?", "It combines s and p shell shapes to form equivalent directional axes, optimizing covalent matches."),
                    ActiveRecallCard("State Le Chatelier's behavior under stress.", "Equilibria adjust configuration settings to actively oppose and counter external stress actions.")
                )
                "Electromagnetism" -> listOf(
                    ActiveRecallCard("What is the electric field inside a hollow metal shell charger?", "Exactly zero, because free charges disperse evenly on outside boundaries to reach potential equilibriums."),
                    ActiveRecallCard("How do magnetic vectors act on perpendicular charge drift?", "They exert maximum side-force according to Lorentz rules, keeping speeds constant but redirecting paths."),
                    ActiveRecallCard("Explain how Lenz's law expresses energy conservation.", "Induced current establishes fields that oppose magnetic changes, requiring mechanical work to drive flux.")
                )
                "Optics & Wave Physics" -> listOf(
                    ActiveRecallCard("Why does light frequency remain unchanged when entering lenses?", "Frequency represents wave counts per second, which depends on physical source transitions rather than path mediums."),
                    ActiveRecallCard("What is the criteria for forming magnified real images using concave mirrors?", "We place targets in front of focal points but within focal limit ranges to focus rays back on testing fields."),
                    ActiveRecallCard("Contrast wave interference against standard particle collisions.", "Wave amplitudes add/subtract constructively or destructively to create patterns, while particles merely add net mass.")
                )
                "Mechanics & Kinematics" -> listOf(
                    ActiveRecallCard("Why does static friction exceed moving friction values?", "Because at rest, microscopic surface teeth settle deeply together, requiring higher threshold force to dislodge."),
                    ActiveRecallCard("What preserves angular momentum inside revolving systems?", "Absence of external unbalanced torque forces keeps rotational speeds constant within radii changes."),
                    ActiveRecallCard("What establishes elastic collisions separate from plastic ones?", "Elastic collisions preserve cumulative kinetic energy along with raw vector linear momentum.")
                )
                else -> listOf(
                    ActiveRecallCard("What validates dimensional equations of physical models?", "Each additive term inside an equality must hold identical base SI units dimensions."),
                    ActiveRecallCard("How does retrieval frequency prevent active memory decay?", "Recalling concepts triggers synaptic updates, cooling down the brain's forget curve trends."),
                    ActiveRecallCard("Why are graphical visualizations valuable in entrance questions?", "They compress complex values into slope/area vectors that can be checked in seconds.")
                )
            }

            val fallbackTemplateQuiz = when (quizType) {
                "Assertion-Reason" -> when (category) {
                    "Thermodynamics" -> listOf(
                        QuizQuestion("Assertion (A): Isothermal processes occur slowly. Reason (R): System needs ample time to exchange heat with surroundings to maintain constant temperature.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "Isothermal changes require slow expansion to keep temperature constant via thermal contact."),
                        QuizQuestion("Assertion (A): CP is always greater than CV. Reason (R): CP includes the work of expansion under constant pressure.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "At constant pressure, gas expands and does work against ambient atmosphere, requiring extra heat energy."),
                        QuizQuestion("Assertion (A): Free expansion of an ideal gas produces no change in temperature. Reason (R): In free expansion into a vacuum, work done is zero.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "No external resistance means no work is done, and adiabatic vacuum means no heat change, so internal energy remains constant."),
                        QuizQuestion("Assertion (A): Reversible processes do not produce thermodynamic entropy in the universe. Reason (R): Reversible changes are executed quasi-statically.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "Quasi-static steps operate in absolute equilibrium, avoiding irreversible entropy dispersion."),
                        QuizQuestion("Assertion (A): No engine can be 100% efficient. Reason (R): The second law of thermodynamics requires some heat to be rejected to a colder sink.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "Efficiency η = 1 - Tc/Th; since Tc cannot be absolute zero, efficiency must be strictly less than 1.")
                    )
                    "Mathematics" -> listOf(
                        QuizQuestion("Assertion (A): Every continuous function is differentiable. Reason (R): f(x) = |x| is continuous at x=0 but not differentiable there.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 3, "Continuity is a prerequisite for differentiability, but not all continuous functions are differentiable; sharp corners reject derivatives."),
                        QuizQuestion("Assertion (A): Definite integrals have unique real values. Reason (R): Integrals with bound parameters resolve the indefinite constant '+C'.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "Bounded integration eliminates arbitrary constant terms, resulting in unique mathematical scalars."),
                        QuizQuestion("Assertion (A): The dot product of two mutually perpendicular vectors is zero. Reason (R): The cosine of 90 degrees is exactly zero.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "A·B = |A||B| cos(θ). Perpendicular vectors form θ = 90°, and cos(90°) = 0, so the dot product is zero."),
                        QuizQuestion("Assertion (A): d/dx [ f(g(x)) ] = f'(g(x)) * g'(x). Reason (R): This is known as the Chain Rule in differential calculus.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "Chain rule regulates compounding rates of inner functions on exterior bounds."),
                        QuizQuestion("Assertion (A): The function f(x) = x^3 is strictly increasing on R. Reason (R): The derivative f'(x) = 3x^2 is non-negative everywhere.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "A positive derivative indicates mounting slopes, which satisfies the increasing function criteria.")
                    )
                    else -> listOf(
                        QuizQuestion("Assertion (A): $cleanTopic is governed by specific biological, chemical, or physical laws. Reason (R): Any system change must comply with conservation of mass or energy.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "All chemical, physical and biological systems adhere to fundamental conservation laws during dynamic transformations of $cleanTopic."),
                        QuizQuestion("Assertion (A): Studying $cleanTopic helps us predict state changes under ambient conditions. Reason (R): Experimental measurements can validate theoretical formulas of $cleanTopic.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "We study $cleanTopic models is to accurately analyze, predict, and optimize performance across varying conditions."),
                        QuizQuestion("Assertion (A): Formulas of $cleanTopic must stay dimensionally consistent. Reason (R): Dimensional checks assure the alignment of base units across mathematical models.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "Dimensional sanity rules require equal metrics on both sides of any valid equation of $cleanTopic."),
                        QuizQuestion("Assertion (A): Standard reference variables specify the rate limits of $cleanTopic. Reason (R): Constant indicators let us compare behavior across experimental controls.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "Reference standards are necessary to define control baselines for $cleanTopic measurements."),
                        QuizQuestion("Assertion (A): Systems can reach a stable state of thermodynamic equilibrium in $cleanTopic. Reason (R): Mutual interactions balance out under isolated boundaries.", listOf("Both A & R are true, and R is correct explanation of A", "Both A & R are true, but R is NOT correct explanation of A", "A is true but R is false", "A is false but R is true"), 0, "Isolated systems naturally relax over time, resolving potential gradients to achieve equilibrium.")
                    )
                }
                "Numerical" -> when (category) {
                    "Thermodynamics" -> listOf(
                        QuizQuestion("Determine the thermal efficiency of a Carnot cycle operating between reservoirs at 300K and 600K.", listOf("25%", "50%", "66%", "75%"), 1, "η = 1 - (300/600) = 0.50 or 50%."),
                        QuizQuestion("An ideal gas expands isothermally at 300K. If volume doubles, what is work done by 1 mole (R = 8.3 J/mol K)?", listOf("1204 J", "1724 J", "2480 J", "3110 J"), 1, "W = nRT ln(2) = 1 * 8.3 * 300 * 0.693 ≈ 1726 J."),
                        QuizQuestion("A thermodynamic system absorbs 1000 J of heat and performs 400 J of external work. Find change in internal energy.", listOf("400 J", "600 J", "1000 J", "1400 J"), 1, "dU = dQ - dW = 1000 - 400 = 600 J."),
                        QuizQuestion("For a diatomic gas, the ratio of specific heats (γ) is approximately:", listOf("1.33", "1.40", "1.67", "2.00"), 1, "Diatomic gases have 5 degrees of freedom, giving γ = 7/5 = 1.40."),
                        QuizQuestion("How much heat is rejected by a Carnot engine absorbing 2000 J at 800K and rejecting at 400K?", listOf("500 J", "1000 J", "1500 J", "2000 J"), 1, "Q_cold/Q_hot = T_cold/T_hot => Q_cold = 2000 * (400/800) = 1000 J.")
                    )
                    "Mathematics" -> listOf(
                        QuizQuestion("Evaluate definite integral ∫ from 0 to 2 of 3x^2 dx.", listOf("4", "6", "8", "12"), 2, "Integral is x^3 evaluated from 0 to 2, yielding 2^3 - 0 = 8."),
                        QuizQuestion("Calculate the slope of tangent to the function y = 2x^2 - 3x + 1 at x = 2.", listOf("1", "3", "5", "7"), 2, "dy/dx = 4x - 3. At x=2, slope is 4(2) - 3 = 5."),
                        QuizQuestion("Find the dot product of vectors A = (2, 3) and B = (4, -1).", listOf("5", "8", "10", "12"), 0, "A·B = (2*4) + (3*-1) = 8 - 3 = 5."),
                        QuizQuestion("Evaluate the limit of (x^2 - 4)/(x - 2) as x approaches 2.", listOf("2", "4", "6", "8"), 1, "Factor numerator: (x-2)(x+2)/(x-2) = x+2. Limit as x->2 is 2+2 = 4."),
                        QuizQuestion("Find the area enclosed by y = x and x-axis from x = 0 to x = 4.", listOf("4", "8", "12", "16"), 1, "∫ from 0 to 4 of x dx = [x^2/2] = 16/2 - 0 = 8.")
                    )
                    else -> listOf(
                        QuizQuestion("Under standard conditions, a test system of $cleanTopic consumes 100 J. If work output is 40 J, find its net internal energy change.", listOf("40 J", "60 J", "100 J", "140 J"), 1, "dU = dQ - dW = 100 - 40 = 60 Joules of energy change in $cleanTopic."),
                        QuizQuestion("Calculate the kinetic rate fraction of $cleanTopic if it doubles for every 10 degree rise in constant temperature from 20°C to 30°C.", listOf("1.0", "2.0", "3.0", "4.0"), 1, "The temperature coefficient defines rate scaling which is 2.0 in standard conditions."),
                        QuizQuestion("Find the work done in transporting a system weight across 5 meters with 12 N net force along the direction of $cleanTopic vectors.", listOf("12 J", "24 J", "48 J", "60 J"), 3, "Work = Force * Distance = 12 * 5 = 60 Joules."),
                        QuizQuestion("An active concentration of $cleanTopic reaches index ratio 10^-4. Find the log acidity score of the compound.", listOf("4", "-4", "10", "14"), 0, "The negative log of 10^-4 yields a positive score concentration value of 4."),
                        QuizQuestion("If mass of a moving kinetic particle in a $cleanTopic field doubles, its kinetic motion energy becomes:", listOf("Half", "Unchanged", "Doubled", "Quadrupled"), 2, "Kinetic energy (KE = 0.5 * m * v2) is directly proportional to mass coefficient.")
                    )
                }
                else -> when (category) {
                    "Thermodynamics" -> listOf(
                        QuizQuestion("Which state variable remains constant during a perfect Isochoric process?", listOf("Temperature", "Pressure", "Volume", "Enthalpy"), 2, "Isochoric processes maintain constant volume, meaning work W = P * dV = 0."),
                        QuizQuestion("What scale of reference is calibrated using Kelvin's absolute zero temperature standard?", listOf("Ideal Gas scale", "Thermodynamic kinetic scale", "Mercury fluid expansion", "Fahrenheit scale"), 1, "Kelvin's scale is based on Carnot thermodynamics limits and is independent of thermometer substance."),
                        QuizQuestion("The change in internal energy during a cyclic process is always equal to:", listOf("Net work done", "Net heat absorbed", "Zero", "Infinity"), 2, "Internal energy is a state function; in a complete cycle, the initial and final states are identical, so dU = 0."),
                        QuizQuestion("Which of the following processes represents water boiling at standard pressure?", listOf("Isothermal and Isobaric", "Adiabatic and Isochoric", "Isothermal and Isochoric", "Adiabatic and Isobaric"), 0, "Phase change occurs at constant temperature (isothermal) and atmospheric pressure (isobaric)."),
                        QuizQuestion("What is the efficiency limit of a thermodynamic engine according to Carnot's theorem?", listOf("Determined by working fluid", "Dependent on source/sink temperature", "Exactly 100%", "Independent of gas cycles"), 1, "Carnot efficiency η is determined solely by absolute temperatures T_hot and T_cold.")
                    )
                    "Mathematics" -> listOf(
                        QuizQuestion("What is the derivative of sec(x) with respect to x?", listOf("tan^2(x)", "sec(x) tan(x)", "sec^2(x)", "1 / cos(x)"), 1, "d/dx(sec x) = sec x * tan x is a standard calculus result."),
                        QuizQuestion("What is the standard definite area interpretation of Definite Integral?", listOf("Slope of tangent", "Accumulated rate factor", "Signed area under curve", "Normal vector"), 2, "Integral computes the Riemann sum representational area under f(x) from a to b."),
                        QuizQuestion("The cross product of two parallel vectors yields which outcome?", listOf("Scalar multiplier", "Unit Vector", "Zero Vector", "Opposing values"), 2, "Cross product involves sin(θ); since parallel vectors have θ = 0, product is zero."),
                        QuizQuestion("Which rule evaluates limits of indeterminate forms like 0/0 by differentiating?", listOf("Euler's Method", "Mean Value Rule", "L'Hopital's Rule", "Simpson's Rule"), 2, "L'Hopital's states limit of f(x)/g(x) equals limit of f'(x)/g'(x) under indeterminate criteria."),
                        QuizQuestion("Which function is transcendental (non-algebraic) in nature?", listOf("y = x^2 - 4", "y = sqrt(x)", "y = ln(x)", "y = 3/x"), 2, "Logarithmic, trigonometric, and exponential functions are transcendental.")
                    )
                    "Chemistry" -> listOf(
                        QuizQuestion("Which hybridization state of carbon exists in Ethylene (C2H4)?", listOf("sp", "sp2", "sp3", "dsp2"), 1, "Double bonded carbons form three sigma bonds, requiring sp2 hybridization."),
                        QuizQuestion("Which periodic element maintains the highest scale electronegativity rating?", listOf("Oxygen", "Chlorine", "Fluorine", "Neon"), 2, "Fluorine holds highest electronegativity rating of 4.0 on Pauling's scale."),
                        QuizQuestion("Which orbital structure has a spherical spatial distribution?", listOf("s-orbital", "p-orbital", "d-orbital", "f-orbital"), 0, "The s-orbital is spherically symmetrical around atomic nuclei."),
                        QuizQuestion("What governs the speed of chemical reactions without being consumed?", listOf("Reactant concentration", "Catalyst agent", "Solvent selection", "Gibbs Free energy"), 1, "Catalysts provide lower activation pathways without permanent chemical degradation."),
                        QuizQuestion("The pH of basic solutions at 25 degrees Celsius is always:", listOf("Less than 7", "Exactly equal to 7", "Greater than 7", "Equal to 0"), 2, "Basic indicators hold low hydrogen ion density, meaning pH range is 7 to 14.")
                    )
                    "Electromagnetism" -> listOf(
                        QuizQuestion("What physical law relates the electric field to enclosed charge?", listOf("Ohm's Law", "Faraday's Law", "Gauss's Law", "Ampere's Law"), 2, "Gauss's Law links electric flux over closed surface with net enclosed charge Q/ε0."),
                        QuizQuestion("The SI unit of capacitance is named the:", listOf("Ohm", "Farad", "Henry", "Tesla"), 1, "Capacitance C = Q/V is measured in Farads (F)."),
                        QuizQuestion("What property measures a wire's opposition to passing electrical current?", listOf("Conductance", "Inductance", "Resistance", "Capacitance"), 2, "Resistance R opposes charge drift vectors, modeled by Ohm's Law (V=IR)."),
                        QuizQuestion("Which particle carriers drift in copper wires to generate current?", listOf("Protons", "Positrons", "Free Electrons", "Copper Ions"), 2, "Metal conductivity relies heavily on drift of mobile valence electrons."),
                        QuizQuestion("What happens to total resistance of resistors sorted in parallel?", listOf("Increases", "Decreases", "Stays constant", "Doubles always"), 1, "Adding parallel options expands area of flow pathways, lowering net resistance.")
                    )
                    "Optics & Wave Physics" -> listOf(
                        QuizQuestion("What parameter remains constant when light transitions between materials of different refractive index?", listOf("Speed", "Wavelength", "Frequency", "Angle of refraction"), 2, "Frequency is source-dependent and stays absolutely constant during refraction."),
                        QuizQuestion("Which phenomenon proves the transverse wave nature of light waves?", listOf("Interference", "Diffraction", "Polarization", "Refraction"), 2, "Only transverse waves can undergo complete linear spatial polarization."),
                        QuizQuestion("A virtual, erect, and magnified image can be formed using a:", listOf("Concave Mirror", "Convex Mirror", "Plane Mirror", "Diverging Lens"), 0, "Concave mirrors form virtual magnified views when object lies inside the focus."),
                        QuizQuestion("The bending of wave vectors around sharp edges of solid structures is called:", listOf("Refraction", "Reflection", "Diffraction", "Dispersion"), 2, "Diffraction occurs when wavelengths match aperture sizes, scattering wavefronts."),
                        QuizQuestion("What relationship links photon energy strictly to wave parameters?", listOf("E = mc²", "E = h * frequency", "E = p * v", "E = h / wavelength"), 1, "Planck's relation states photon quantum energy is proportional to wave frequency.")
                    )
                    "Mechanics & Kinematics" -> listOf(
                        QuizQuestion("What is the slope of a position-time graph representational of?", listOf("Acceleration", "Velocity", "Force", "Displacement"), 1, "Velocity is rate of change of position, corresponds to position slope value."),
                        QuizQuestion("An item in stable equilibrium rests on a surface. Its center of gravity must:", listOf("Be outside its base", "Be at the highest point", "Lie vertically over its supporting base", "Move dynamically"), 2, "Alignment of gravity vector within base limits secures stable balance configurations."),
                        QuizQuestion("What force opposes sliding motion along contact surfaces?", listOf("Tensional force", "Centripetal force", "Frictional force", "Gravitational force"), 2, "Friction reacts opposite to relative sliding trajectories."),
                        QuizQuestion("If mass of a moving object doubles while speed remains constant, its kinetic energy becomes:", listOf("Half", "Unchanged", "Doubled", "Quadrupled"), 2, "KE = 0.5 * m * v²; doubling m directly doubles the kinetic energy factor."),
                        QuizQuestion("What physical property represents an object's resistance to rotational acceleration?", listOf("Mass scale", "Linear momentum", "Moment of inertia", "Angular torque"), 2, "Moment of inertia (I) acts as rotational equivalent of inertial mass.")
                    )
                    else -> listOf(
                        QuizQuestion("What represents the primary objective when studying $cleanTopic?", listOf("Memorizing definitions", "Analyzing principles & behaviors", "Minimizing practical experiments", "Avoiding units checks"), 1, "To master $cleanTopic, students should analyze underlying physical, mathematical or chemical processes instead of simple rote memorization."),
                        QuizQuestion("Which parameter controls the spatial density of elements in any $cleanTopic system?", listOf("Static mass", "Unit volume", "Density (Mass / Volume)", "Vector orientation"), 2, "Density dictates atomic density and structural compactness in any $cleanTopic sample."),
                        QuizQuestion("What standard unit measures work and heat metrics within $cleanTopic processes?", listOf("Pascal", "Watt", "Newton", "Joule"), 3, "Joule (J) is the unified SI energy unit across mechanical, chemical, and thermal systems."),
                        QuizQuestion("Which condition stabilizes dynamic interactions inside $cleanTopic boundaries?", listOf("Thermal equilibrium", "Pressure spikes", "Infinite expansion", "Thermal degradation"), 0, "When net interaction potentials equalize, the system settles into stable equilibrium."),
                        QuizQuestion("What role do reference controls play when measuring $cleanTopic reactions?", listOf("Distorting outcomes", "Defining a baseline for comparison", "Increasing experimental noise", "Removing numeric values"), 1, "Controls are essential to isolate independent variables from background influences.")
                    )
                }
            }

            // Combine and pad hfQuiz and fallbackTemplateQuiz up to exactly 5 questions
            val finalQuizList = mutableListOf<QuizQuestion>()
            val seenQuestionsForFinalQuiz = mutableSetOf<String>()

            if (hfQuiz != null) {
                for (q in hfQuiz) {
                    if (finalQuizList.size >= 5) break
                    val normalizedQ = q.question.trim().lowercase()
                    if (!seenQuestionsForFinalQuiz.contains(normalizedQ)) {
                        seenQuestionsForFinalQuiz.add(normalizedQ)
                        finalQuizList.add(q)
                    }
                }
            }

            // Pad with fallback template questions if we have fewer than 5
            for (q in fallbackTemplateQuiz.shuffled()) {
                if (finalQuizList.size >= 5) break
                val normalizedQ = q.question.trim().lowercase()
                if (!seenQuestionsForFinalQuiz.contains(normalizedQ)) {
                    seenQuestionsForFinalQuiz.add(normalizedQ)
                    finalQuizList.add(q)
                }
            }

            // Ensure exactly 5 quiz questions
            val quiz = finalQuizList.take(5)

            val session = StudySession(
                topic = cleanTopic,
                noteContent = topicOrNote,
                summary = summaryText,
                eli12 = eli12Text,
                mnemonicsJson = JsonHelper.stringListToJson(mnemonics),
                keyPointsJson = JsonHelper.stringListToJson(keyPoints),
                activeRecallJson = JsonHelper.activeRecallListToJson(activeRecall),
                quizJson = JsonHelper.quizListToJson(quiz),
                difficulty = difficulty,
                isExamTomorrowMode = isExamTomorrow,
                formulasJson = JsonHelper.stringListToJson(formulas),
                examQuestionsJson = JsonHelper.stringListToJson(rawExam),
                eli11 = cli11Text,
                derivationsJson = JsonHelper.stringListToJson(derivations),
                commonMistakesJson = JsonHelper.stringListToJson(mistakes),
                predictionTopicsJson = JsonHelper.stringListToJson(predictions),
                priorityScore = 91
            )
            studySessionDao.insertSession(session)
            triggerStreakUpdate()
            incrementXp(15) // Grant standard XP reward
            return@withContext session
        }

        // Build prompt tailored to CBSE/JEE, formulas, derivations, predictions, Class 11 level, and OCR
        val systemPrompt = """
            You are an expert AI Study Buddy designed for CBSE Grade 11/12 secondary students, JEE and NEET aspirants.
            Your response must be returned STRICTLY as a raw JSON object matching the requested schema. 
            Do not include any normal chat text. Only output valid parseable JSON.
        """.trimIndent()

        val prompt = """
            Input Topic or Note Materials:
            "$topicOrNote"

            Target Difficulty / Exam Setting: $targetExam ($difficulty)
            Selected Quiz Question Style: $quizType
            Is "Exam Tomorrow" (Intensive Review Mode) Active?: $isExamTomorrow

            Please perform these tasks and output the exact JSON structure specified below:
            
            1. Short Summary: Draft a crisp, intuitive summary that explains core principles.
               - If Is Exam Tomorrow is true, make this a high-impact, rapid-fire exam bullet checklist (absolutely vital focus items).
               - Otherwise, make it a smooth conceptual explanation focused on understanding rather than boring rote memory data points.
               
            2. Explain Like I'm 12 (ELI12): Break down the hardest concept in this topic using a funny, highly visual analogy or metaphor. Use psychology to build immediate sticky associations.
            
            3. Mnemonics: Provide list of 2-3 clever mnemonics, abbreviation acrostics, or fun memory hooks.
            
            4. Key Points: List of 4-6 crucial core points.
            
            5. Formulas: List of 3-5 crucial physical, chemical, or mathematical formulas related to this topic, with term explanations.
            
            6. examQuestions: List of 2-3 likely exam questions often asked in school boards or entrance tests.
            
            7. eli11: A rewritten, simple explanation aimed at Class 11 students with friendly guidance.
            
            8. derivations: List of 1-3 important mathematical derivations related to the topic.
            
            9. commonMistakes: List of 2-3 common misconceptions or mathematical mistakes students make here.
            
            10. predictedTopics: List of 2-3 predicted future assessment topics in JEE/NEET.
            
            11. priorityScore: Integer rating from 1 to 100 on how heavily weighted this topic is in exam papers.
            
            12. Active Recall Cards: Provide 3 short question and answer self-test pairs format to activate physical retrieval pathways.
            
            13. Quiz: Provide exactly 5 quiz questions. Each question must match the requested style: $quizType (e.g. MCQ, Assertion-Reason, Numerical, Short Answers) and include:
               - question: Clear testing item
               - options: Exactly 4 distinct multiple choice strings
               - correctOptionIndex: Integer index (0 to 3) representing the correct answer
               - explanation: A helpful psychological breakdown explaining why it's correct.
               
            Scale questions complexity appropriately to difficulty stage $difficulty and exam model $targetExam.

            JSON Schema Requirement:
            {
               "summary": "String detailing crisp summary",
               "eli12": "String detailing metaphor story",
               "mnemonics": ["Mnemonic 1 string", "Mnemonic 2 string"],
               "keyPoints": ["Point 1", "Point 2"],
               "formulas": ["Formula 1", "Formula 2"],
               "examQuestions": ["Question 1", "Question 2"],
               "eli11": "String explaining to Class 11",
               "derivations": ["Derivation 1", "Derivation 2"],
               "commonMistakes": ["Mistake 1", "Mistake 2"],
               "predictedTopics": ["Prediction 1", "Prediction 2"],
               "priorityScore": 88,
               "activeRecall": [
                  { "question": "Question 1?", "answer": "Answer 1" }
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

        val parsed = try {
            val apiResponse = RetrofitClient.service.generateContent(apiKey, request)
            val rawJson = apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw IllegalStateException("AI returned an empty response.")
            val cleanedJson = cleanJson(rawJson)
            JsonHelper.moshi.adapter(GeminiStudyResponse::class.java).fromJson(cleanedJson)
                ?: throw IllegalArgumentException("Failed to decode JSON data configuration.")
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to offline mode generator if API call or JSON parsing fails to ensure absolute resilience
            val warningMsg = "⚠️ Note: API returned an error (${e.localizedMessage ?: "HTTP 403 Forbidden"}). Running offline fallback mode. Please configure or verify your custom Gemini API Key in the Settings panel!\n\n"
            return@withContext generateStudyGuide(
                topicOrNote = topicOrNote,
                difficulty = difficulty,
                isExamTomorrow = isExamTomorrow,
                quizType = quizType,
                targetExam = targetExam,
                apiRouterModel = apiRouterModel,
                localModelEnabledFlag = true, // Force offline fallback mode
                customApiKey = customApiKey,
                warningNotice = warningMsg
            )
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
            isExamTomorrowMode = isExamTomorrow,
            
            formulasJson = JsonHelper.stringListToJson(parsed.formulas ?: emptyList()),
            examQuestionsJson = JsonHelper.stringListToJson(parsed.examQuestions ?: emptyList()),
            eli11 = parsed.eli11 ?: "This topic breaks down core concepts in simple stages for Class 11 high school classes.",
            derivationsJson = JsonHelper.stringListToJson(parsed.derivations ?: emptyList()),
            commonMistakesJson = JsonHelper.stringListToJson(parsed.commonMistakes ?: emptyList()),
            predictionTopicsJson = JsonHelper.stringListToJson(parsed.predictedTopics ?: emptyList()),
            priorityScore = parsed.priorityScore ?: 85
        )

        studySessionDao.insertSession(session)
        triggerStreakUpdate()
        incrementXp(50) // High reward for successfully querying Gemini AI!

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
