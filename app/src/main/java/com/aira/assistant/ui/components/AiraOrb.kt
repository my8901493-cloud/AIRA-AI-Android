package com.aira.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aira.assistant.domain.model.VoiceState
import com.aira.assistant.ui.theme.AiraBlueAccent
import com.aira.assistant.ui.theme.AiraCyanGlow
import com.aira.assistant.ui.theme.AiraErrorCoral
import com.aira.assistant.ui.theme.AiraPurpleDeep
import com.aira.assistant.ui.theme.AiraPurplePrimary
import com.aira.assistant.ui.theme.AiraSpeakingRose
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Central Animated AI Orb for AIRA AI.
 *
 * Implements the 6 required Orb states without placing unnecessary clutter underneath:
 * - IDLE: Slow breathing animation with deep purple-blue core.
 * - LISTENING: Audio-reactive sapphire/cyan glow expanding with live microphone RMS energy.
 * - THINKING: Subtle multi-ring orbital rotation with purple-cyan sweep gradient.
 * - SPEAKING: Harmonic audio-reactive wave modulation reacting to TTS output amplitude.
 * - INTERRUPTING: Immediate snap ripple transition into active listening state.
 * - ERROR: Clear coral-red diagnostic ring pulse.
 */
@Composable
fun AiraOrb(
    voiceState: VoiceState,
    audioLevel: Float,
    orbSize: Dp = 250.dp,
    onOrbTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AiraOrbInfinite")

    // Slow breathing phase for IDLE and base modulation
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )

    // Continuous orbital rotation angle for THINKING & SPEAKING harmonics
    val rotationDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (voiceState == VoiceState.THINKING) 2800 else 8000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationDegrees"
    )

    // Harmonic wave phase for SPEAKING & LISTENING ripples
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val smoothedAudio by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 75, easing = FastOutSlowInEasing),
        label = "smoothedAudio"
    )

    val primaryCoreColor by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.IDLE -> AiraPurplePrimary
            VoiceState.LISTENING -> AiraCyanGlow
            VoiceState.THINKING -> AiraPurplePrimary
            VoiceState.SPEAKING -> AiraSpeakingRose
            VoiceState.INTERRUPTING -> AiraBlueAccent
            VoiceState.ERROR -> AiraErrorCoral
        },
        animationSpec = tween(durationMillis = 260),
        label = "primaryCoreColor"
    )

    val secondaryHaloColor by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.IDLE -> AiraBlueAccent
            VoiceState.LISTENING -> AiraPurplePrimary
            VoiceState.THINKING -> AiraCyanGlow
            VoiceState.SPEAKING -> AiraBlueAccent
            VoiceState.INTERRUPTING -> AiraCyanGlow
            VoiceState.ERROR -> Color(0xFF9F1239)
        },
        animationSpec = tween(durationMillis = 260),
        label = "secondaryHaloColor"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(orbSize)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOrbTap
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            val dynamicScale = when (voiceState) {
                VoiceState.IDLE -> breatheScale
                VoiceState.LISTENING -> 1.0f + (smoothedAudio * 0.26f)
                VoiceState.THINKING -> 0.98f + 0.04f * sin(wavePhase)
                VoiceState.SPEAKING -> 1.0f + (smoothedAudio * 0.30f) + 0.04f * cos(wavePhase)
                VoiceState.INTERRUPTING -> 1.15f
                VoiceState.ERROR -> 0.96f + 0.05f * sin(wavePhase * 2f)
            }

            // 1. Outer ambient radial aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryCoreColor.copy(alpha = 0.32f),
                        secondaryHaloColor.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * dynamicScale.coerceAtMost(1.28f)
                ),
                radius = maxRadius * dynamicScale.coerceAtMost(1.28f),
                center = center
            )

            // 2. Audio-reactive harmonic wave ring during LISTENING / SPEAKING / INTERRUPTING
            if (voiceState == VoiceState.LISTENING ||
                voiceState == VoiceState.SPEAKING ||
                voiceState == VoiceState.INTERRUPTING
            ) {
                val ringRadius = maxRadius * (0.66f + smoothedAudio * 0.18f)
                drawCircle(
                    color = primaryCoreColor.copy(alpha = 0.45f),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = secondaryHaloColor.copy(alpha = 0.25f),
                    radius = ringRadius * 1.14f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 3. Rotating orbital ring for THINKING & subtle orbital sheen in other states
            rotate(degrees = rotationDegrees, pivot = center) {
                val orbitalRadius = maxRadius * 0.62f * dynamicScale.coerceIn(0.88f, 1.12f)
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryCoreColor.copy(
                                alpha = if (voiceState == VoiceState.THINKING) 0.9f else 0.4f
                            ),
                            secondaryHaloColor.copy(
                                alpha = if (voiceState == VoiceState.THINKING) 0.85f else 0.3f
                            ),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    radius = orbitalRadius,
                    center = center,
                    style = Stroke(
                        width = if (voiceState == VoiceState.THINKING) 5.dp.toPx() else 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }

            // 4. Inner luminous core sphere
            val coreRadius = maxRadius * 0.48f * dynamicScale.coerceIn(0.85f, 1.18f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.88f),
                        primaryCoreColor.copy(alpha = 0.92f),
                        secondaryHaloColor.copy(alpha = 0.78f),
                        AiraPurpleDeep.copy(alpha = 0.95f)
                    ),
                    center = Offset(
                        x = center.x - coreRadius * 0.22f,
                        y = center.y - coreRadius * 0.22f
                    ),
                    radius = coreRadius * 1.25f
                ),
                radius = coreRadius,
                center = center
            )

            // 5. Glassmorphic specular highlight on top-left of the sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.42f),
                        Color.Transparent
                    ),
                    center = Offset(
                        x = center.x - coreRadius * 0.32f,
                        y = center.y - coreRadius * 0.32f
                    ),
                    radius = coreRadius * 0.55f
                ),
                radius = coreRadius * 0.55f,
                center = Offset(
                    x = center.x - coreRadius * 0.32f,
                    y = center.y - coreRadius * 0.32f
                )
            )
        }
    }
}

/**
 * Side-edge screen glow overlay rendered when Screen Understanding / MediaProjection is active.
 */
@Composable
fun ScreenSharingEdgeGlow(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isActive) return
    val infiniteTransition = rememberInfiniteTransition(label = "ScreenEdgeGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val edgeWidth = 14.dp.toPx()
        // Left edge glow
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(AiraCyanGlow.copy(alpha = glowAlpha), Color.Transparent),
                startX = 0f,
                endX = edgeWidth * 2.5f
            )
        )
        // Right edge glow
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, AiraPurplePrimary.copy(alpha = glowAlpha)),
                startX = size.width - edgeWidth * 2.5f,
                endX = size.width
            )
        )
    }
}
