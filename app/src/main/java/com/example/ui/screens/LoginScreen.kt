package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.StudyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: StudyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSignUpMode by remember { mutableStateOf(false) }
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var showGoogleAuthSheet by remember { mutableStateOf(false) }
    var showGithubAuthSheet by remember { mutableStateOf(false) }
    var isAuthenticatingSocial by remember { mutableStateOf(false) }
    var authSocialEmail by remember { mutableStateOf("") }
    var authSocialName by remember { mutableStateOf("") }

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val accountsPrefs = remember {
        context.getSharedPreferences("app_accounts", android.content.Context.MODE_PRIVATE)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Large Premium App Logo Indicator
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🚀",
                    fontSize = 40.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "STUDY BUDDY AI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isSignUpMode) "Begin your personalized Class 11/12 learning odyssey" else "Power up your JEE, NEET and CBSE preparation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Main Glassmorphic Card Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (isSignUpMode) "Create Account" else "Sign In",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Nickname input (only during sign up)
                    AnimatedVisibility(visible = isSignUpMode) {
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_nickname_input"),
                            label = { Text("Aspirant Nickname") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "User Nickname"
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    // Email field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_email_input"),
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email address icon"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input"),
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password shield icon"
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Authenticate Button
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_auth_button"),
                        shape = RoundedCornerShape(14.dp),
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            // Validations
                            if (email.trim().isEmpty() || password.trim().isEmpty()) {
                                Toast.makeText(context, "Please configure both email and password.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                Toast.makeText(context, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (isSignUpMode && nickname.trim().isEmpty()) {
                                Toast.makeText(context, "Please type a nickname.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val cleanEmail = email.trim().lowercase()

                            if (isSignUpMode) {
                                // Save local registration
                                if (accountsPrefs.contains(cleanEmail)) {
                                    Toast.makeText(context, "Account already exists! Please log in.", Toast.LENGTH_LONG).show()
                                } else {
                                    accountsPrefs.edit()
                                        .putString(cleanEmail, password)
                                        .putString("${cleanEmail}_name", nickname.trim())
                                        .apply()

                                    Toast.makeText(context, "Registration Complete! Please sign in with your credentials.", Toast.LENGTH_LONG).show()
                                    isSignUpMode = false
                                    password = ""
                                }
                            } else {
                                // Log in validation
                                val registeredPassword = accountsPrefs.getString(cleanEmail, null)
                                if (registeredPassword == null) {
                                    Toast.makeText(context, "No account matches this email. Please click Sign Up below.", Toast.LENGTH_LONG).show()
                                } else if (registeredPassword != password) {
                                    Toast.makeText(context, "Invalid password. Please verify and retry.", Toast.LENGTH_LONG).show()
                                } else {
                                    val userNick = accountsPrefs.getString("${cleanEmail}_name", "Aspirant") ?: "Aspirant"
                                    viewModel.setLoginStatus(cleanEmail, true)
                                    Toast.makeText(context, "Logged in as $userNick!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text(
                            text = if (isSignUpMode) "Register & Start Study" else "Login to Dashboard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Social login divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                        Text(
                            text = "Or continue with",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Social buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Google button
                        OutlinedButton(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                showGoogleAuthSheet = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("continue_with_google_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = 1.dp
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            GoogleLogoIcon(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        // GitHub button
                        OutlinedButton(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                showGithubAuthSheet = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("continue_with_github_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF181717),
                                contentColor = Color.White
                            ),
                            border = null,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            GithubLogoIcon(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GitHub",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Mode Toggle text spacer
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSignUpMode) "Already configured an account? " else "Don't have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isSignUpMode) "Sign In" else "Sign Up",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            isSignUpMode = !isSignUpMode
                            password = ""
                        }
                        .testTag("toggle_login_mode_button")
                )
            }
        }
    }

    // Google Account Chooser Dialog
    if (showGoogleAuthSheet) {
        val enteredEmail = email.trim()
        val isValidEnteredEmail = enteredEmail.contains("@") && enteredEmail.contains(".")
        val dynamicEmail = if (isValidEnteredEmail) enteredEmail else "aspirant.scholar@gmail.com"
        val dynamicName = if (isValidEnteredEmail) enteredEmail.substringBefore("@").replaceFirstChar { it.uppercase() } else "Scholar Aspirant"

        AlertDialog(
            onDismissRequest = { if (!isAuthenticatingSocial) showGoogleAuthSheet = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GoogleLogoIcon(modifier = Modifier.size(28.dp))
                    Text(
                        text = "Sign in with Google",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Choose an account to continue to Study Buddy AI",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Account Option 1 (Default Scholar)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isAuthenticatingSocial) {
                                authSocialEmail = "aspirant.scholar@gmail.com"
                                authSocialName = "Scholar Aspirant"
                                isAuthenticatingSocial = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "S",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Scholar Aspirant",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "aspirant.scholar@gmail.com",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Account Option 2 (Dynamic Option)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isAuthenticatingSocial) {
                                authSocialEmail = dynamicEmail
                                authSocialName = dynamicName
                                isAuthenticatingSocial = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dynamicName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = dynamicName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dynamicEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showGoogleAuthSheet = false },
                    enabled = !isAuthenticatingSocial
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // GitHub Account Authorization Dialog
    if (showGithubAuthSheet) {
        val enteredEmail = email.trim()
        val isValidEnteredEmail = enteredEmail.contains("@") && enteredEmail.contains(".")
        val dynamicEmail = if (isValidEnteredEmail) enteredEmail else "coder.aspirant@github.com"
        val dynamicName = if (isValidEnteredEmail) enteredEmail.substringBefore("@").replaceFirstChar { it.uppercase() } else "Dev Aspirant"
        
        AlertDialog(
            onDismissRequest = { if (!isAuthenticatingSocial) showGithubAuthSheet = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GithubLogoIcon(modifier = Modifier.size(28.dp))
                    Text(
                        text = "Authorize Study Buddy AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Study Buddy AI is requesting permissions to access your public profile and email address.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF181717)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "GH",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = dynamicName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dynamicEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "• Public profile info (avatar, nickname)\n• Primary email address ($dynamicEmail)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        authSocialEmail = dynamicEmail
                        authSocialName = dynamicName
                        isAuthenticatingSocial = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2EA44F), // GitHub green button!
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isAuthenticatingSocial
                ) {
                    Text("Authorize com.aistudio")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGithubAuthSheet = false },
                    enabled = !isAuthenticatingSocial
                ) {
                    Text("Decline")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Active spinner simulation
    if (isAuthenticatingSocial) {
        LaunchedEffect(isAuthenticatingSocial) {
            kotlinx.coroutines.delay(1200) // Beautiful delay
            isAuthenticatingSocial = false
            showGoogleAuthSheet = false
            showGithubAuthSheet = false
            
            // Save login details to accounts SharedPreferences so they can function exactly like registered users
            val cleanEmail = authSocialEmail.trim().lowercase()
            accountsPrefs.edit()
                .putString(cleanEmail, "oauth_social_token_placeholder")
                .putString("${cleanEmail}_name", authSocialName)
                .apply()
                
            // Trigger actual logged-in status
            viewModel.setLoginStatus(cleanEmail, true)
            Toast.makeText(context, "Successfully authenticated as $authSocialName!", Toast.LENGTH_LONG).show()
        }

        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {},
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Securing OAuth connection...",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.White, shape = CircleShape)
            .border(1.dp, Color.LightGray.copy(alpha = 0.5f), CircleShape)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            drawArc(
                color = Color(0xFFEA4335), // Red
                startAngle = 135f,
                sweepAngle = 100f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx())
            )
            drawArc(
                color = Color(0xFFFBBC05), // Yellow
                startAngle = 45f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx())
            )
            drawArc(
                color = Color(0xFF34A853), // Green
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx())
            )
            drawArc(
                color = Color(0xFF4285F4), // Blue
                startAngle = -135f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        Text(
            text = "G",
            color = Color(0xFF4285F4),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun GithubLogoIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF181717), shape = CircleShape)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            
            drawCircle(
                color = Color.White,
                radius = r * 0.75f,
                center = center
            )
            
            val earPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x - r * 0.5f, center.y - r * 0.3f)
                lineTo(center.x - r * 0.6f, center.y - r * 0.8f)
                lineTo(center.x - r * 0.2f, center.y - r * 0.5f)
                close()
                moveTo(center.x + r * 0.5f, center.y - r * 0.3f)
                lineTo(center.x + r * 0.6f, center.y - r * 0.8f)
                lineTo(center.x + r * 0.2f, center.y - r * 0.5f)
                close()
            }
            drawPath(earPath, color = Color.White)
            
            drawCircle(
                color = Color(0xFF181717),
                radius = r * 0.12f,
                center = androidx.compose.ui.geometry.Offset(center.x - r * 0.22f, center.y - r * 0.05f)
            )
            drawCircle(
                color = Color(0xFF181717),
                radius = r * 0.12f,
                center = androidx.compose.ui.geometry.Offset(center.x + r * 0.22f, center.y - r * 0.05f)
            )
        }
    }
}
