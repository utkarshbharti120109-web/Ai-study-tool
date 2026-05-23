package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.StudyReminder
import com.example.viewmodel.StudyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    viewModel: StudyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var inputTitle by remember { mutableStateOf("") }
    var inputHour by remember { mutableStateOf("9") }
    var inputMinute by remember { mutableStateOf("00") }

    var simulatedReviewTopic by remember { mutableStateOf("Mental Retrieval") }

    LaunchedEffect(sessions) {
        if (sessions.isNotEmpty()) {
            simulatedReviewTopic = sessions.first().topic
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "MEMORY OVERLAPPING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Spaced Repetitions",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .testTag("add_reminder_fab")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Schedule", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = "Establish cognitive intervals. Studying topics at scheduled milestones defeats the decay curve.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Simulating personalized alert center CTA
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("personalized_study_reminders_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔔", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "PERSONALIZED ALARM CENTER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.2.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Spaced repetition works best when alerts nudge you exactly when memory decays. Test our tailored algorithm nudge below!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val alertText = "📚 Spaced Recalled Alert! It has been an elegant interval since you reviewed '$simulatedReviewTopic'. Tap to start a 5-question review and lock in the neuro-links! 🔥"
                            viewModel.simulateNotification(context, StudyReminder(title = simulatedReviewTopic, hour = 9, minute = 0))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = "Simulate icon")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Trigger Spaced Recalled Alert Nudge")
                    }
                }
            }
        }

        item {
            Text(
                text = "ACTIVE STUDY ALARMS LIST",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (reminders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No study alarms scheduled yet.\nTap '+' above to set custom study intervals.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(reminders) { alarm ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reminder_item_${alarm.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Alarm clock", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = alarm.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            val formattedTime = String.format("%02d:%02d", alarm.hour, alarm.minute)
                            Text(
                                text = "Daily interval at $formattedTime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = alarm.isEnabled,
                            onCheckedChange = { viewModel.toggleReminder(alarm) },
                            modifier = Modifier.testTag("reminder_switch_${alarm.id}")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { viewModel.deleteReminder(alarm.id) },
                            modifier = Modifier.testTag("delete_reminder_button_${alarm.id}")
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    "Define Personalized Block",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("Subject / Focus Area") },
                        placeholder = { Text("e.g. Biological Science") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_reminder_title_field")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputHour,
                            onValueChange = { inputHour = it },
                            label = { Text("Hour (0-23)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("add_reminder_hour_field")
                        )
                        OutlinedTextField(
                            value = inputMinute,
                            onValueChange = { inputMinute = it },
                            label = { Text("Minute (0-59)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("add_reminder_minute_field")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val hour = inputHour.toIntOrNull() ?: 9
                        val minute = inputMinute.toIntOrNull() ?: 0
                        val valHour = hour.coerceIn(0, 23)
                        val valMinute = minute.coerceIn(0, 59)
                        val title = if (inputTitle.trim().isEmpty()) "Daily Study Interval" else inputTitle

                        viewModel.addReminder(title, valHour, valMinute)

                        // Reset
                        inputTitle = ""
                        inputHour = "9"
                        inputMinute = "00"
                        showAddDialog = false
                    },
                    modifier = Modifier.testTag("add_reminder_confirm_button")
                ) {
                    Text("Schedule Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
