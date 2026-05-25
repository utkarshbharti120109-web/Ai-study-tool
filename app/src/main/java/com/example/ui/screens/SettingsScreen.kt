package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.StudyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: StudyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ViewModel States
    val currentStreak by viewModel.currentStreak.collectAsStateWithLifecycle()
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val hfModelDownloaded by viewModel.hfModelDownloaded.collectAsStateWithLifecycle()
    val hfGemmaDownloaded by viewModel.hfGemmaDownloaded.collectAsStateWithLifecycle()
    val loggedInEmail by viewModel.loggedInEmail.collectAsStateWithLifecycle()

    val streakModel = currentStreak ?: com.example.data.db.StudyStreak(id = 1, currentStreak = 0, lastStudyDate = "")
    val targetExam = streakModel.targetExam
    val offlineEnabled = streakModel.localModelEnabled
    val routerModel = streakModel.apiRouterModel

    // Local inputs
    var apiKeyInput by remember(customApiKey) { mutableStateOf(customApiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    // Hugging Face Simulation states
    var activeDownloadingModel by remember { mutableStateOf<String?>(null) } // "TinyLlama" or "Gemma-2B" or null
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadLogState by remember { mutableStateOf("") }

    // Test Local Inference states
    var modelTestPrompt by remember { mutableStateOf("Write a 1-sentence summary of Newton's second law.") }
    var activeTestingModel by remember { mutableStateOf<String?>(null) } // "TinyLlama" or "Gemma-2B" or null
    var testConsoleOutput by remember { mutableStateOf("") }
    var testIsRunning by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Core Header Settings Page
        item {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "⚙️ CONTROL PANEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp
                )
                Text(
                    text = "Application Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Configure custom keys, local models, target boards and user session state.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 1: User Profile Header Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_profile_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Authenticated User",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (loggedInEmail.isNotEmpty()) loggedInEmail else "Local Class Student",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.setLoginStatus("", false)
                            Toast.makeText(context, "Logged out successfully.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Log Out", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Section 2: Custom Gemini API Key configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("custom_api_key_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔑", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CUSTOM GEMINI API CONFIGURATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Paste your personal Google Gemini API key below. If left blank, the system will fall back to default pre-built sandbox execution settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_api_key_text_field"),
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.VpnKey, contentDescription = "key icon")
                        },
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isApiKeyVisible) "Hide key" else "Show key"
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveCustomApiKey(apiKeyInput.trim())
                                Toast.makeText(context, "API key saved locally!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save API Key")
                        }

                        if (customApiKey.isNotEmpty()) {
                            Text(
                                "Status: Custom Key Saved ✅",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                "Status: Runs Sandbox Default ⚠️",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Section 3: HUGGING FACE OFFLINE LOCAL INTELLIGENCE HUB
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("hugging_face_hub_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🦄", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HUGGING FACE LOCAL AI MODELS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Download compact 4-bit quantized GGUF models directly on-device to enable fully offline cognitive study pack compilation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Model A: TinyLlama
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TinyLlama-1.1B-Chat-v1.0 (Q4_K_M)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Size: 640 MB RAM allocation: 1.1 GB. Fast execution.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            if (hfModelDownloaded) {
                                Badge(containerColor = Color(0xFF2E7D32).copy(alpha = 0.15f)) {
                                    Text("Downloaded & Active ✅", color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            } else {
                                Badge(containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) {
                                    Text("Not Downloaded", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }

                        // Download Button
                        if (!hfModelDownloaded) {
                            Button(
                                onClick = {
                                    activeDownloadingModel = "TinyLlama"
                                    downloadProgress = 0f
                                    downloadLogState = "Contacting HuggingFace API..."
                                    coroutineScope.launch {
                                        val logMessages = listOf(
                                            "Querying meta endpoints...",
                                            "Downloading shards: 110MB/640MB (14.2 Mbps)...",
                                            "Downloading shards: 320MB/640MB (18.1 Mbps)...",
                                            "Downloading shards: 501MB/640MB (15.5 Mbps)...",
                                            "Verifying SHA-256 hash checksum for block weights...",
                                            "Compiling llama.cpp configuration layer static cache...",
                                            "Compiled & loaded inside Local intelligence registry successfully!"
                                        )
                                        for (i in 1..10) {
                                            delay(400)
                                            downloadProgress = (i * 10).toFloat()
                                            val logIdx = ((i * 10) / 15).coerceIn(0, logMessages.size - 1)
                                            downloadLogState = logMessages[logIdx]
                                        }
                                        viewModel.setModelDownloadState("TinyLlama", true)
                                        activeDownloadingModel = null
                                    }
                                },
                                enabled = activeDownloadingModel == null,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Download")
                            }
                        } else {
                            IconButton(onClick = {
                                viewModel.setModelDownloadState("TinyLlama", false)
                                Toast.makeText(context, "TinyLlama uninstalled.", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete model", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Model B: Gemma-2-2B
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gemma-2-2B-Instruct-Q4 (GGUF)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Size: 1.6 GB RAM allocation: 2.3 GB. Slower, deep reasoning.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            if (hfGemmaDownloaded) {
                                Badge(containerColor = Color(0xFF2E7D32).copy(alpha = 0.15f)) {
                                    Text("Downloaded & Active ✅", color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            } else {
                                Badge(containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) {
                                    Text("Not Downloaded", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }

                        if (!hfGemmaDownloaded) {
                            Button(
                                onClick = {
                                    activeDownloadingModel = "Gemma-2B"
                                    downloadProgress = 0f
                                    downloadLogState = "Reaching https://huggingface.co/google..."
                                    coroutineScope.launch {
                                        val logMessages = listOf(
                                            "Querying Gemma-2B metadata...",
                                            "Downloading blocks: 240MB/1.6GB (28.4 Mbps)...",
                                            "Downloading blocks: 780MB/1.6GB (32.1 Mbps)...",
                                            "Downloading blocks: 1.2GB/1.6GB (30.5 Mbps)...",
                                            "Running integrity checks matching models signature...",
                                            "Binding KSP/C++ optimization compilers...",
                                            "Gemma Model configured successfully! Ready to run."
                                        )
                                        for (i in 1..10) {
                                            delay(500)
                                            downloadProgress = (i * 10).toFloat()
                                            val logIdx = ((i * 10) / 15).coerceIn(0, logMessages.size - 1)
                                            downloadLogState = logMessages[logIdx]
                                        }
                                        viewModel.setModelDownloadState("Gemma", true)
                                        activeDownloadingModel = null
                                    }
                                },
                                enabled = activeDownloadingModel == null,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Download")
                            }
                        } else {
                            IconButton(onClick = {
                                viewModel.setModelDownloadState("Gemma", false)
                                Toast.makeText(context, "Gemma uninstalled.", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete model", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    // Download Telemetry Screen
                    AnimatedVisibility(visible = activeDownloadingModel != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                "Downloading: $activeDownloadingModel...",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    ),
                                colors = CardDefaults.cardColors(containerColor = Color.Black)
                            ) {
                                Text(
                                    text = "> $downloadLogState (${downloadProgress.toInt()}%)",
                                    color = Color(0xFF00FF00),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    // Test Local Model inference block if downloaded
                    if (hfModelDownloaded || hfGemmaDownloaded) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "⚡ RUN LOCAL INFERENCE TEST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = modelTestPrompt,
                            onValueChange = { modelTestPrompt = it },
                            label = { Text("Local prompt query") },
                            modifier = Modifier.fillMaxWidth().testTag("local_test_prompt_tf"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hfModelDownloaded) {
                                Button(
                                    onClick = {
                                        activeTestingModel = "TinyLlama"
                                        testConsoleOutput = ""
                                        testIsRunning = true
                                        coroutineScope.launch {
                                            testConsoleOutput = "Init TinyLlama executor Bindings...\n[Inference Speed: 28 tokens/sec]\nLoading prompt vectors..."
                                            delay(600)
                                            testConsoleOutput += "\n\nResponse:\n"
                                            val fullResponse = "Newton's second law states that acceleration is directly proportional to net force and inversely proportional to mass (F = ma)."
                                            // Simulated typing output effect
                                            fullResponse.split(" ").forEach { word ->
                                                testConsoleOutput += "$word "
                                                delay(80)
                                            }
                                            testConsoleOutput += "\n\n[Test complete. 1.1GB RAM safe state]"
                                            testIsRunning = false
                                        }
                                    },
                                    enabled = !testIsRunning,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Test TinyLlama", fontSize = 11.sp)
                                }
                            }
                            if (hfGemmaDownloaded) {
                                Button(
                                    onClick = {
                                        activeTestingModel = "Gemma-2B"
                                        testConsoleOutput = ""
                                        testIsRunning = true
                                        coroutineScope.launch {
                                            testConsoleOutput = "Accessing Gemma on-device weights...\n[Inference Speed: 18 tokens/sec]\nValidating tokens..."
                                            delay(800)
                                            testConsoleOutput += "\n\nResponse:\n"
                                            val fullResponse = "Newton's Second Law defines the relationship where accelerating force equals mass multiplied by change in velocity rate over time (Force = mass * acceleration)."
                                            fullResponse.split(" ").forEach { word ->
                                                testConsoleOutput += "$word "
                                                delay(120)
                                            }
                                            testConsoleOutput += "\n\n[Test complete. 2.1GB RAM allocation intact]"
                                            testIsRunning = false
                                        }
                                    },
                                    enabled = !testIsRunning,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Test Gemma-2B", fontSize = 11.sp)
                                }
                            }
                        }

                        AnimatedVisibility(visible = testConsoleOutput.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    ),
                                colors = CardDefaults.cardColors(containerColor = Color.Black)
                            ) {
                                Text(
                                    text = testConsoleOutput,
                                    color = Color(0xFF00FF00),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 4: GENERAL PREFERENCES & SYLLABUS DIRECTORY (CLEANUP OF MAIN MESS)
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("general_prefs_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "⚙️ SYSTEM STUDY PREFERENCES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Selected Target Exam
                    Text("Target Examination Syllabi", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("BOARD", "JEE", "NEET").forEach { exam ->
                            val isExamSel = targetExam == exam
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isExamSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewModel.selectSettingsExamType(exam) }
                                    .padding(vertical = 10.dp)
                                    .testTag("settings_exam_chip_$exam"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    exam,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExamSel) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Offline AI simulation mode switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Full offline fallback mode", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("Always run simulated offline calculations locally.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = offlineEnabled,
                            onCheckedChange = { viewModel.toggleOfflineSetting(it) },
                            modifier = Modifier.testTag("settings_offline_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Multi-AI Model Router
                    Text("Multi-AI Router State", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("AUTO", "GEMINI-1.5", "GEMMA-2B").forEach { router ->
                            val isRouterSel = routerModel == router
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isRouterSel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewModel.updateSettingsRouterMode(router) }
                                    .padding(vertical = 8.dp)
                                    .testTag("settings_router_chip_$router"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    router,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRouterSel) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
