package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AgentLog
import com.example.model.LogStatus
import com.example.model.TaskState
import com.example.overlay.DonarkFloatingOverlayService
import com.example.ui.theme.DonarkBlue
import com.example.ui.theme.DonarkCard
import com.example.ui.theme.DonarkCyan
import com.example.ui.theme.DonarkDeepDark
import com.example.ui.theme.DonarkError
import com.example.ui.theme.DonarkPurple
import com.example.ui.theme.DonarkSuccess
import com.example.ui.theme.DonarkSurface
import com.example.ui.theme.DonarkSurfaceVariant
import com.example.ui.theme.DonarkTextPrimary
import com.example.ui.theme.DonarkTextSecondary
import com.example.ui.theme.DonarkWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonarkHomeScreen(
    viewModel: DonarkViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val taskState by viewModel.taskState.collectAsState()
    val currentTaskName by viewModel.currentTaskName.collectAsState()
    val currentStepDescription by viewModel.currentStepDescription.collectAsState()
    val lastSpokenResponse by viewModel.lastSpokenResponse.collectAsState()
    val pendingMessage by viewModel.pendingMessage.collectAsState()
    val awaitingConfirmation by viewModel.awaitingConfirmation.collectAsState()
    val confirmationPrompt by viewModel.confirmationPrompt.collectAsState()
    val logs by viewModel.logs.collectAsState()

    val isListening by viewModel.isListening.collectAsState()
    val isContinuousVoiceMode by viewModel.isContinuousVoiceMode.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()

    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsState()
    val currentForegroundPackage by viewModel.currentForegroundPackage.collectAsState()

    var inputCommandText by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DonarkDeepDark,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DonarkSurface,
                    titleContentColor = DonarkTextPrimary
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Neural Network Symbol Badge
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DonarkSurfaceVariant)
                                .border(1.dp, DonarkCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "☊",
                                color = DonarkCyan,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(
                                text = "DONARK AI",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                            )
                            Text(
                                text = "Agentic Voice Assistant",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = DonarkTextSecondary
                                )
                            )
                        }
                    }
                },
                actions = {
                    // Continuous Voice Mode Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (isContinuousVoiceMode) "Voice ON" else "Voice OFF",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isContinuousVoiceMode) DonarkCyan else DonarkTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isContinuousVoiceMode,
                            onCheckedChange = { viewModel.toggleContinuousVoiceMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DonarkDeepDark,
                                checkedTrackColor = DonarkCyan,
                                uncheckedThumbColor = DonarkTextSecondary,
                                uncheckedTrackColor = DonarkSurfaceVariant
                            ),
                            modifier = Modifier.testTag("voice_mode_switch")
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {

            // 1. Accessibility & Overlay Permission Awareness Card
            item {
                AccessibilityStatusBanner(
                    context = context,
                    isAccessibilityConnected = isAccessibilityConnected,
                    currentForegroundPackage = currentForegroundPackage
                )
            }

            // 2. Active Neural Agent Stage Card
            item {
                NeuralAgentStageCard(
                    taskState = taskState,
                    currentTaskName = currentTaskName,
                    currentStepDescription = currentStepDescription,
                    lastSpokenResponse = lastSpokenResponse,
                    isSpeaking = isSpeaking
                )
            }

            // 3. Consequential Action / Pending Message Confirmation Card
            if (awaitingConfirmation && confirmationPrompt != null) {
                item {
                    PendingActionConfirmationCard(
                        prompt = confirmationPrompt!!,
                        pendingMessage = pendingMessage,
                        onConfirm = { viewModel.confirmPendingAction(true) },
                        onCancel = { viewModel.confirmPendingAction(false) }
                    )
                }
            }

            // 4. Interactive Voice & Command Console
            item {
                VoiceCommandConsole(
                    isListening = isListening,
                    isContinuous = isContinuousVoiceMode,
                    inputText = inputCommandText,
                    onInputTextChanged = { inputCommandText = it },
                    onSubmitCommand = {
                        if (inputCommandText.isNotBlank()) {
                            viewModel.submitCommand(inputCommandText)
                            inputCommandText = ""
                        }
                    },
                    onToggleMic = {
                        if (isListening) {
                            viewModel.stopListening()
                        } else {
                            viewModel.startListening()
                        }
                    }
                )
            }

            // 5. Quick Prompt Chips
            item {
                QuickCommandChips(
                    onSelectCommand = { cmd ->
                        viewModel.submitCommand(cmd)
                    }
                )
            }

            // 6. End-to-End Test Suite Runner (Critical CUJs)
            item {
                CriticalE2ETestCard(
                    onRunScenario = { scenarioCmd ->
                        viewModel.submitCommand(scenarioCmd)
                    }
                )
            }

            // 7. Real-Time Action & Verification Logs
            item {
                AgentExecutionLogsCard(logs = logs)
            }
        }
    }
}

@Composable
private fun AccessibilityStatusBanner(
    context: Context,
    isAccessibilityConnected: Boolean,
    currentForegroundPackage: String?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DonarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isAccessibilityConnected) DonarkSuccess else DonarkWarning)
                    )
                    Text(
                        text = if (isAccessibilityConnected) "Screen Awareness: Active" else "Screen Awareness: Needs Setup",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = DonarkTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                if (!isAccessibilityConnected) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DonarkCyan),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("enable_accessibility_button")
                    ) {
                        Text("Enable", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            if (Settings.canDrawOverlays(context)) {
                                DonarkFloatingOverlayService.start(context)
                            } else {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DonarkCyan),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("floating_overlay_button")
                    ) {
                        Text("Launch Overlay", fontSize = 12.sp)
                    }
                }
            }

            if (currentForegroundPackage != null) {
                Text(
                    text = "Observed Foreground: $currentForegroundPackage",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = DonarkTextSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun NeuralAgentStageCard(
    taskState: TaskState,
    currentTaskName: String,
    currentStepDescription: String,
    lastSpokenResponse: String,
    isSpeaking: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DonarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DonarkCyan.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "NEURAL AGENT STATE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = DonarkTextSecondary,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                TaskStateBadge(state = taskState)
            }

            // Neural Network Canvas
            NeuralNetworkVisualizer(
                taskState = taskState,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Current Task Name
            Text(
                text = currentTaskName,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = DonarkTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            // Current Step Description
            if (currentStepDescription.isNotBlank()) {
                Surface(
                    color = DonarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = currentStepDescription,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = DonarkCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Spoken response bubble
            if (lastSpokenResponse.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DonarkSurface)
                        .padding(10.dp)
                ) {
                    Text(
                        text = if (isSpeaking) "🗣️" else "💬",
                        fontSize = 14.sp
                    )
                    Text(
                        text = lastSpokenResponse,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = DonarkTextPrimary,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskStateBadge(state: TaskState) {
    val (color, text) = when (state) {
        TaskState.IDLE -> DonarkTextSecondary to "IDLE"
        TaskState.LISTENING -> DonarkBlue to "LISTENING"
        TaskState.UNDERSTANDING -> DonarkPurple to "UNDERSTANDING"
        TaskState.PLANNING -> DonarkPurple to "PLANNING"
        TaskState.EXECUTING -> DonarkCyan to "EXECUTING"
        TaskState.OBSERVING -> DonarkCyan to "OBSERVING"
        TaskState.VERIFYING -> DonarkCyan to "VERIFYING"
        TaskState.RECOVERING -> DonarkWarning to "RECOVERING"
        TaskState.COMPLETED -> DonarkSuccess to "COMPLETED"
        TaskState.FAILED -> DonarkError to "FAILED"
        TaskState.CANCELLED -> DonarkWarning to "CANCELLED"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PendingActionConfirmationCard(
    prompt: String,
    pendingMessage: com.example.model.PendingMessage?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DonarkSurfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DonarkWarning.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Confirmation Required",
                    tint = DonarkWarning,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "CONFIRMATION REQUIRED",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = DonarkWarning,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = DonarkTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            )

            if (pendingMessage != null) {
                Surface(
                    color = DonarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "To: ${pendingMessage.contactName} (${pendingMessage.targetApp})",
                            style = MaterialTheme.typography.labelSmall.copy(color = DonarkCyan)
                        )
                        Text(
                            text = "\"${pendingMessage.messageText}\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = DonarkTextPrimary,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = DonarkCyan, contentColor = DonarkDeepDark),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("confirm_send_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DonarkTextSecondary),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cancel_send_button")
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun VoiceCommandConsole(
    isListening: Boolean,
    isContinuous: Boolean,
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSubmitCommand: () -> Unit,
    onToggleMic: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "MicPulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MicPulseScale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = DonarkSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Large Microphone Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .scale(if (isListening) micScale else 1f)
                        .clip(CircleShape)
                        .background(
                            if (isListening) DonarkCyan else DonarkSurfaceVariant
                        )
                        .clickable { onToggleMic() }
                        .testTag("mic_fab_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = if (isListening) DonarkDeepDark else DonarkCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Command Text Field for fast keyboard query & testing
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    placeholder = {
                        Text(
                            text = if (isListening) "Listening to voice..." else "Enter command or speak...",
                            color = DonarkTextSecondary,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DonarkCyan,
                        unfocusedBorderColor = DonarkSurfaceVariant,
                        focusedTextColor = DonarkTextPrimary,
                        unfocusedTextColor = DonarkTextPrimary,
                        cursorColor = DonarkCyan
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSubmitCommand() }),
                    trailingIcon = {
                        if (inputText.isNotBlank()) {
                            IconButton(
                                onClick = onSubmitCommand,
                                modifier = Modifier.testTag("submit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Submit",
                                    tint = DonarkCyan
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickCommandChips(onSelectCommand: (String) -> Unit) {
    val quickPrompts = listOf(
        "Open YouTube",
        "Open WhatsApp",
        "Open Spotify",
        "What's on the screen?",
        "Stop the song",
        "YouTube lo funny videos search cheyyi"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "QUICK COMMANDS",
            style = MaterialTheme.typography.labelSmall.copy(
                color = DonarkTextSecondary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(quickPrompts) { prompt ->
                FilterChip(
                    selected = false,
                    onClick = { onSelectCommand(prompt) },
                    label = { Text(prompt, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = DonarkSurface,
                        labelColor = DonarkTextPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = DonarkSurfaceVariant,
                        enabled = true,
                        selected = false
                    ),
                    modifier = Modifier.testTag("chip_${prompt.take(10).replace(" ", "_")}")
                )
            }
        }
    }
}

@Composable
private fun CriticalE2ETestCard(onRunScenario: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DonarkSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "CRITICAL E2E TESTS (VERIFICATION SUITE)",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = DonarkCyan,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            // Test 1: Spotify Kesariya Playback & Interruption
            E2ETestRow(
                title = "1. Spotify Kesariya & Stop",
                description = "Open Spotify, search Kesariya, play -> Stop song -> Play again",
                onExecute = {
                    onRunScenario("Open Spotify, search Kesariya, and play it")
                }
            )

            // Test 2: WhatsApp Chat & Send Confirmation
            E2ETestRow(
                title = "2. WhatsApp Amma Message & Send",
                description = "Open Amma chat -> 'I will reach at 10 AM' -> 'Change to 11 AM' -> 'Send it'",
                onExecute = {
                    onRunScenario("Open Amma chat")
                }
            )

            // Test 3: YouTube Search & First Result
            E2ETestRow(
                title = "3. YouTube Dogs First Result",
                description = "Open YouTube, search funny dog videos, and open the first result",
                onExecute = {
                    onRunScenario("Open YouTube, search funny dog videos, and open the first result")
                }
            )

            // Test 4: Screen Reader
            E2ETestRow(
                title = "4. Screen Awareness & Reader",
                description = "Extracts and reads visible elements on current screen",
                onExecute = {
                    onRunScenario("What is on the screen?")
                }
            )
        }
    }
}

@Composable
private fun E2ETestRow(
    title: String,
    description: String,
    onExecute: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DonarkSurfaceVariant)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = DonarkTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = DonarkTextSecondary,
                    fontSize = 11.sp
                )
            )
        }

        IconButton(
            onClick = onExecute,
            modifier = Modifier.testTag("run_test_${title.take(3)}")
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Run Test",
                tint = DonarkCyan
            )
        }
    }
}

@Composable
private fun AgentExecutionLogsCard(logs: List<AgentLog>) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DonarkSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ACTION & VERIFICATION LOGS",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = DonarkTextSecondary,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            if (logs.isEmpty()) {
                Text(
                    text = "No actions executed yet. Speak or type a command above.",
                    style = MaterialTheme.typography.bodySmall.copy(color = DonarkTextSecondary),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                logs.take(15).forEach { log ->
                    LogItemRow(log = log, timeString = timeFormat.format(Date(log.timestamp)))
                }
            }
        }
    }
}

@Composable
private fun LogItemRow(log: AgentLog, timeString: String) {
    val (statusColor, statusIcon) = when (log.status) {
        LogStatus.SUCCESS -> DonarkSuccess to "✓"
        LogStatus.FAILED -> DonarkError to "✗"
        LogStatus.IN_PROGRESS -> DonarkCyan to "⟳"
        LogStatus.WARNING -> DonarkWarning to "!"
        LogStatus.INFO -> DonarkBlue to "i"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "[$timeString]",
            style = MaterialTheme.typography.labelSmall.copy(
                color = DonarkTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        )

        Surface(
            color = statusColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = statusIcon,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.stepName,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
            Text(
                text = log.details,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = DonarkTextPrimary,
                    fontSize = 11.sp
                )
            )
        }
    }
}
