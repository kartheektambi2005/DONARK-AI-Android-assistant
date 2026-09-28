package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.model.TaskState
import com.example.ui.theme.DonarkBlue
import com.example.ui.theme.DonarkCyan
import com.example.ui.theme.DonarkError
import com.example.ui.theme.DonarkPurple
import com.example.ui.theme.DonarkSuccess
import com.example.ui.theme.DonarkWarning
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Canvas visualizing a neural network structure with glowing nodes
 * and synaptic connections that dynamically respond to the DONARK agent state.
 */
@Composable
fun NeuralNetworkVisualizer(
    taskState: TaskState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "NeuralPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (taskState) {
                    TaskState.LISTENING -> 800
                    TaskState.UNDERSTANDING, TaskState.PLANNING -> 500
                    TaskState.EXECUTING, TaskState.VERIFYING -> 600
                    else -> 2200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    val stateColor = when (taskState) {
        TaskState.IDLE -> DonarkCyan
        TaskState.LISTENING -> DonarkBlue
        TaskState.UNDERSTANDING, TaskState.PLANNING -> DonarkPurple
        TaskState.EXECUTING, TaskState.OBSERVING, TaskState.VERIFYING -> DonarkCyan
        TaskState.RECOVERING -> DonarkWarning
        TaskState.COMPLETED -> DonarkSuccess
        TaskState.FAILED, TaskState.CANCELLED -> DonarkError
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f

        // Node network definition
        val nodes = listOf(
            Offset(centerX - 130f, centerY - 25f),
            Offset(centerX - 70f, centerY + 30f),
            Offset(centerX - 35f, centerY - 35f),
            Offset(centerX, centerY),
            Offset(centerX + 40f, centerY + 32f),
            Offset(centerX + 80f, centerY - 28f),
            Offset(centerX + 130f, centerY + 20f),
            Offset(centerX - 85f, centerY - 45f),
            Offset(centerX + 95f, centerY + 40f)
        )

        // Synaptic connections (lines)
        val connections = listOf(
            0 to 1, 0 to 7, 1 to 2, 7 to 2, 2 to 3, 1 to 3,
            3 to 4, 3 to 5, 4 to 6, 5 to 6, 4 to 8, 5 to 8
        )

        // Draw connections
        for ((startIdx, endIdx) in connections) {
            val p1 = nodes[startIdx]
            val p2 = nodes[endIdx]
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(stateColor.copy(alpha = 0.35f), DonarkPurple.copy(alpha = 0.25f)),
                    start = p1,
                    end = p2
                ),
                start = p1,
                end = p2,
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }

        // Draw dynamic synaptic wave pulse
        if (taskState != TaskState.IDLE) {
            val waveY = centerY + sin(wavePhase) * 12f
            drawLine(
                color = stateColor.copy(alpha = 0.5f),
                start = Offset(centerX - 140f, waveY),
                end = Offset(centerX + 140f, waveY),
                strokeWidth = 1.5f
            )
        }

        // Draw Nodes
        for ((index, node) in nodes.withIndex()) {
            val isCenter = index == 3
            val nodeRadius = if (isCenter) 9f * pulse else 5.5f * (if (index % 2 == 0) pulse else 1f)
            val glowRadius = nodeRadius * 2.2f

            // Outer glow
            drawCircle(
                color = stateColor.copy(alpha = if (isCenter) 0.35f else 0.2f),
                radius = glowRadius,
                center = node
            )

            // Inner core
            drawCircle(
                color = if (isCenter) Color.White else stateColor,
                radius = nodeRadius,
                center = node
            )
        }
    }
}
