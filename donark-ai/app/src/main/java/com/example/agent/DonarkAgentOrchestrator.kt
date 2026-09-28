package com.example.agent

import android.content.Context
import android.util.Log
import com.example.accessibility.DonarkAccessibilityBridge
import com.example.ai.DonarkAiBrain
import com.example.model.AgentLog
import com.example.model.AgentPlan
import com.example.model.AgentStep
import com.example.model.LogStatus
import com.example.model.PendingMessage
import com.example.model.TaskState
import com.example.tools.ToolExecutor
import com.example.verification.ActionVerifier
import com.example.voice.DonarkSpeechManager
import com.example.voice.DonarkTtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * DONARK Agent Orchestrator.
 * Implements the core loop:
 * LISTEN -> UNDERSTAND -> PLAN -> ACT -> OBSERVE -> VERIFY -> CONTINUE -> RESPOND
 */
class DonarkAgentOrchestrator(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DonarkOrchestrator"
    }

    private val verifier = ActionVerifier(context)
    val toolExecutor = ToolExecutor(context, verifier)
    val brain = DonarkAiBrain()

    lateinit var ttsManager: DonarkTtsManager
    lateinit var speechManager: DonarkSpeechManager

    private val _taskState = MutableStateFlow(TaskState.IDLE)
    val taskState: StateFlow<TaskState> = _taskState.asStateFlow()

    private val _currentTaskName = MutableStateFlow("Ready for command")
    val currentTaskName: StateFlow<String> = _currentTaskName.asStateFlow()

    private val _currentStepDescription = MutableStateFlow("")
    val currentStepDescription: StateFlow<String> = _currentStepDescription.asStateFlow()

    private val _lastSpokenResponse = MutableStateFlow("")
    val lastSpokenResponse: StateFlow<String> = _lastSpokenResponse.asStateFlow()

    private val _pendingMessage = MutableStateFlow<PendingMessage?>(null)
    val pendingMessage: StateFlow<PendingMessage?> = _pendingMessage.asStateFlow()

    private val _awaitingConfirmation = MutableStateFlow(false)
    val awaitingConfirmation: StateFlow<Boolean> = _awaitingConfirmation.asStateFlow()

    private val _confirmationPrompt = MutableStateFlow<String?>(null)
    val confirmationPrompt: StateFlow<String?> = _confirmationPrompt.asStateFlow()

    private val _logs = MutableStateFlow<List<AgentLog>>(emptyList())
    val logs: StateFlow<List<AgentLog>> = _logs.asStateFlow()

    private val _activePlan = MutableStateFlow<AgentPlan?>(null)
    val activePlan: StateFlow<AgentPlan?> = _activePlan.asStateFlow()

    private var currentExecutionJob: Job? = null

    init {
        ttsManager = DonarkTtsManager(context) {
            speechManager.onTtsFinished()
        }

        speechManager = DonarkSpeechManager(context) { recognizedText ->
            handleUserVoiceCommand(recognizedText)
        }
    }

    fun addLog(stepName: String, status: LogStatus, details: String) {
        val newLog = AgentLog(
            stepName = stepName,
            status = status,
            details = details
        )
        _logs.value = listOf(newLog) + _logs.value.take(49)
        Log.d(TAG, "[$status] $stepName: $details")
    }

    fun handleUserVoiceCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return

        addLog("VOICE_INPUT", LogStatus.INFO, "Received: \"$trimmed\"")

        // 1. Check Voice Mode toggle commands first
        val lower = trimmed.lowercase()
        if (lower == "voice mode on" || lower == "start voice mode" || lower == "voice on") {
            setContinuousVoiceMode(true)
            respondWithTts("Continuous voice mode is now on. I am listening.")
            return
        }
        if (lower == "voice mode off" || lower == "stop listening" || lower == "voice off") {
            setContinuousVoiceMode(false)
            respondWithTts("Voice mode turned off.")
            return
        }

        // 2. Check User Interruption / "Stop" command
        if (lower == "stop" || lower == "cancel") {
            handleUserStop()
            return
        }

        // 3. Check Consequential Confirmation Response if awaiting confirmation
        if (_awaitingConfirmation.value) {
            if (lower == "yes" || lower == "send it" || lower == "send" || lower == "confirm" || lower == "yeah" || lower == "sure") {
                _awaitingConfirmation.value = false
                _confirmationPrompt.value = null
                executeConfirmedAction()
                return
            } else if (lower == "no" || lower == "cancel" || lower == "don't send" || lower == "wait") {
                _awaitingConfirmation.value = false
                _confirmationPrompt.value = null
                _taskState.value = TaskState.IDLE
                addLog("CONFIRMATION", LogStatus.INFO, "User cancelled action.")
                respondWithTts("Cancelled. The message was not sent.")
                return
            }
        }

        // 4. Normal command execution pipeline
        currentExecutionJob?.cancel()
        currentExecutionJob = scope.launch(Dispatchers.Main) {
            runAgentLoop(trimmed)
        }
    }

    /**
     * Context-aware stop handling:
     * If media is playing, stops media and keeps listening!
     * If an active agent task is running, cancels the task.
     */
    private fun handleUserStop() {
        val mediaAction = scope.launch(Dispatchers.IO) {
            toolExecutor.stopMedia()
        }

        if (_taskState.value in listOf(TaskState.PLANNING, TaskState.EXECUTING, TaskState.OBSERVING, TaskState.VERIFYING)) {
            currentExecutionJob?.cancel()
            _taskState.value = TaskState.CANCELLED
            addLog("TASK_INTERRUPT", LogStatus.WARNING, "Active task cancelled by user.")
            respondWithTts("Stopped.")
        } else {
            addLog("MEDIA_STOP", LogStatus.SUCCESS, "Stopped playback.")
            respondWithTts("Stopped.")
        }
    }

    /**
     * Fundamental Agent Loop:
     * LISTEN -> UNDERSTAND -> PLAN -> ACT -> OBSERVE -> VERIFY -> CONTINUE -> RESPOND
     */
    private suspend fun runAgentLoop(command: String) {
        _currentTaskName.value = command
        _taskState.value = TaskState.UNDERSTANDING
        addLog("UNDERSTAND", LogStatus.IN_PROGRESS, "Parsing intent: \"$command\"")

        val currentApp = DonarkAccessibilityBridge.getCurrentPackage()
        val currentPending = _pendingMessage.value

        // PLAN phase
        _taskState.value = TaskState.PLANNING
        val plan = brain.planCommand(command, currentPending, currentApp)
        _activePlan.value = plan
        addLog("PLAN", LogStatus.SUCCESS, "Generated plan with ${plan.steps.size} step(s)")

        if (plan.steps.isEmpty()) {
            _taskState.value = TaskState.COMPLETED
            _currentStepDescription.value = "Done"
            return
        }

        // Multi-Step Execution Loop
        var failed = false
        var failureReason = ""

        for ((index, step) in plan.steps.withIndex()) {
            _taskState.value = TaskState.EXECUTING
            _currentStepDescription.value = "[${index + 1}/${plan.steps.size}] ${step.description}"
            addLog("EXECUTE_STEP", LogStatus.IN_PROGRESS, "Step ${index + 1}: ${step.toolName} (${step.description})")

            // Check if this step is preparing a message for WhatsApp
            if (step.toolName == "prepareMessage" || step.toolName == "typeMessage") {
                val targetContact = step.parameters["contact"] ?: currentPending?.contactName ?: "recipient"
                val text = step.parameters["message"] ?: plan.contextData["pendingMessageText"] ?: command
                _pendingMessage.value = PendingMessage(
                    targetApp = "WhatsApp",
                    contactName = targetContact,
                    messageText = text
                )
            }

            // Check if step requires confirmation (e.g. sending a message)
            if (step.toolName == "sendPreparedMessage") {
                val target = _pendingMessage.value?.contactName ?: "the recipient"
                val msgText = _pendingMessage.value?.messageText ?: ""
                _awaitingConfirmation.value = true
                val prompt = "I've prepared the message for $target: \"$msgText\". Do you want me to send it?"
                _confirmationPrompt.value = prompt
                _taskState.value = TaskState.IDLE
                addLog("CONFIRMATION_REQUIRED", LogStatus.INFO, "Awaiting user confirmation to send message")
                respondWithTts(prompt)
                return // Pause loop until user confirms ("Yes" / "Send it")
            }

            // ACT
            val result = toolExecutor.executeTool(step.toolName, step.parameters)

            // OBSERVE & VERIFY
            _taskState.value = TaskState.OBSERVING
            delay(400)
            _taskState.value = TaskState.VERIFYING

            if (result.success) {
                addLog("VERIFIED", LogStatus.SUCCESS, "${step.toolName} verified: ${result.message}")
            } else {
                // Failure Recovery attempt or Honest Failure Report
                _taskState.value = TaskState.RECOVERING
                addLog("VERIFY_FAILED", LogStatus.FAILED, "${step.toolName} verification failed: ${result.message}")

                // Bounded fallback retry for search submission or element tap
                val recovered = attemptRecovery(step)
                if (recovered) {
                    addLog("RECOVERY", LogStatus.SUCCESS, "Recovered from failure for ${step.toolName}")
                } else {
                    failed = true
                    failureReason = result.message
                    break
                }
            }
        }

        if (failed) {
            _taskState.value = TaskState.FAILED
            _currentStepDescription.value = "Failed: $failureReason"
            addLog("TASK_FAILED", LogStatus.FAILED, "Task aborted: $failureReason")
            respondWithTts(failureReason)
        } else {
            _taskState.value = TaskState.COMPLETED
            _currentStepDescription.value = "Task completed successfully"
            addLog("TASK_COMPLETE", LogStatus.SUCCESS, "Finished: $command")

            // Context-specific honest response
            val responseText = when {
                command.lowercase().contains("spotify") && command.lowercase().contains("kesariya") -> "Kesariya is now playing on Spotify."
                command.lowercase().contains("youtube") && command.lowercase().contains("funny dog") -> "I found the results and opened the video."
                command.lowercase().contains("stop") -> "Stopped."
                plan.steps.any { it.toolName == "readScreen" } -> "Here is what's on the screen."
                plan.steps.any { it.toolName == "findImages" } -> "Here are your images."
                else -> "Done."
            }
            respondWithTts(responseText)
        }
    }

    private suspend fun attemptRecovery(step: AgentStep): Boolean {
        return when (step.toolName) {
            "searchCurrentApp" -> {
                // Fallback: try pressing IME Enter or clicking first search icon
                delay(600)
                val node = DonarkAccessibilityBridge.findSearchInputNode()
                if (node != null) {
                    DonarkAccessibilityBridge.submitSearch(node)
                } else false
            }
            "tapElement" -> {
                delay(800)
                val target = step.parameters["element"] ?: ""
                val node = DonarkAccessibilityBridge.findNodeByText(target)
                if (node != null) {
                    DonarkAccessibilityBridge.clickNode(node)
                } else false
            }
            else -> false
        }
    }

    private fun executeConfirmedAction() {
        scope.launch(Dispatchers.Main) {
            _taskState.value = TaskState.EXECUTING
            _currentStepDescription.value = "Sending message..."
            addLog("SENDING", LogStatus.IN_PROGRESS, "Executing user-confirmed message send")

            val result = toolExecutor.sendPreparedMessage()
            if (result.success) {
                _taskState.value = TaskState.COMPLETED
                _currentStepDescription.value = "Message sent"
                _pendingMessage.value = null
                addLog("MESSAGE_SENT", LogStatus.SUCCESS, "Message sent successfully")
                respondWithTts("Message sent.")
            } else {
                _taskState.value = TaskState.FAILED
                _currentStepDescription.value = "Failed to send"
                addLog("MESSAGE_SEND_FAILED", LogStatus.FAILED, result.message)
                respondWithTts("I couldn't send the message.")
            }
        }
    }

    fun setContinuousVoiceMode(enabled: Boolean) {
        speechManager.setContinuousMode(enabled)
        addLog("VOICE_MODE", LogStatus.INFO, "Continuous voice mode set to: $enabled")
    }

    fun startSingleVoiceQuery() {
        _taskState.value = TaskState.LISTENING
        _currentStepDescription.value = "Listening..."
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
        if (_taskState.value == TaskState.LISTENING) {
            _taskState.value = TaskState.IDLE
        }
    }

    private fun respondWithTts(text: String) {
        _lastSpokenResponse.value = text
        speechManager.onTtsStarting()
        ttsManager.speak(text)
    }

    fun shutdown() {
        currentExecutionJob?.cancel()
        speechManager.destroy()
        ttsManager.shutdown()
    }
}
