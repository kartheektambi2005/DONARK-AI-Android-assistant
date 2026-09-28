package com.example.model

import java.util.UUID

/**
 * Explicit task state machine states as specified in DONARK specification.
 */
enum class TaskState {
    IDLE,
    LISTENING,
    UNDERSTANDING,
    PLANNING,
    EXECUTING,
    OBSERVING,
    VERIFYING,
    RECOVERING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class LogStatus {
    INFO,
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    WARNING
}

data class AgentLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val stepName: String,
    val status: LogStatus,
    val details: String
)

data class PendingMessage(
    val targetApp: String = "WhatsApp",
    val contactName: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isConfirmed: Boolean = false
)

data class AgentStep(
    val toolName: String,
    val parameters: Map<String, String> = emptyMap(),
    val description: String,
    val verificationType: String = ""
)

data class AgentPlan(
    val taskId: String = UUID.randomUUID().toString(),
    val originalCommand: String,
    val steps: List<AgentStep>,
    val currentStepIndex: Int = 0,
    val contextData: Map<String, String> = emptyMap()
)

data class VerificationResult(
    val verified: Boolean,
    val message: String,
    val observedState: String = ""
)

data class ToolExecutionResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any?> = emptyMap(),
    val canRecover: Boolean = false
)
