package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessibility.DonarkAccessibilityBridge
import com.example.agent.DonarkAgentOrchestrator
import com.example.model.AgentLog
import com.example.model.PendingMessage
import com.example.model.TaskState
import kotlinx.coroutines.flow.StateFlow

class DonarkViewModel(application: Application) : AndroidViewModel(application) {

    val orchestrator = DonarkAgentOrchestrator(application, viewModelScope)

    val taskState: StateFlow<TaskState> = orchestrator.taskState
    val currentTaskName: StateFlow<String> = orchestrator.currentTaskName
    val currentStepDescription: StateFlow<String> = orchestrator.currentStepDescription
    val lastSpokenResponse: StateFlow<String> = orchestrator.lastSpokenResponse
    val pendingMessage: StateFlow<PendingMessage?> = orchestrator.pendingMessage
    val awaitingConfirmation: StateFlow<Boolean> = orchestrator.awaitingConfirmation
    val confirmationPrompt: StateFlow<String?> = orchestrator.confirmationPrompt
    val logs: StateFlow<List<AgentLog>> = orchestrator.logs

    val isListening: StateFlow<Boolean> = orchestrator.speechManager.isListening
    val isContinuousVoiceMode: StateFlow<Boolean> = orchestrator.speechManager.isContinuousMode
    val isSpeaking: StateFlow<Boolean> = orchestrator.ttsManager.isSpeaking

    val isAccessibilityConnected: StateFlow<Boolean> = DonarkAccessibilityBridge.isServiceConnected
    val currentForegroundPackage: StateFlow<String?> = DonarkAccessibilityBridge.currentPackage

    fun submitCommand(command: String) {
        orchestrator.handleUserVoiceCommand(command)
    }

    fun toggleContinuousVoiceMode(enabled: Boolean) {
        orchestrator.setContinuousVoiceMode(enabled)
    }

    fun startListening() {
        orchestrator.startSingleVoiceQuery()
    }

    fun stopListening() {
        orchestrator.stopListening()
    }

    fun confirmPendingAction(confirmed: Boolean) {
        if (confirmed) {
            orchestrator.handleUserVoiceCommand("Yes")
        } else {
            orchestrator.handleUserVoiceCommand("Cancel")
        }
    }

    override fun onCleared() {
        super.onCleared()
        orchestrator.shutdown()
    }
}
