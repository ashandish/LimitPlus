package com.limitplus.app.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limitplus.app.R
import com.limitplus.app.ui.theme.CoralAlert
import com.limitplus.app.ui.theme.CyanLight
import com.limitplus.app.ui.theme.CyanPrimary
import com.limitplus.app.ui.theme.EmeraldSuccess
import com.limitplus.app.ui.theme.IndigoSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OverlayPromptCard(
    packageName: String,
    appName: String,
    baselineMinutes: Int,
    isRelaunch: Boolean,
    totalUsedSeconds: Long = baselineMinutes * 60L,
    extensionsGrantedToday: Int = 0,
    multiPressEnabled: Boolean = true,
    pauseDelaySeconds: Int = 0,
    promptId: Long = 0L,
    onAddFiveMinutes: () -> Unit,
    onCloseApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sanitizedDelay = pauseDelaySeconds.coerceAtLeast(0)
    val requiredPresses = if (multiPressEnabled) {
        (extensionsGrantedToday + 1).coerceAtLeast(1)
    } else {
        1
    }

    var remainingDelaySeconds by remember(packageName, promptId, sanitizedDelay) {
        mutableIntStateOf(sanitizedDelay)
    }
    var tapsCompleted by remember(packageName, extensionsGrantedToday, multiPressEnabled, promptId) {
        mutableIntStateOf(0)
    }

    val isWaiting = remainingDelaySeconds > 0
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val extendButtonScale = remember { Animatable(1f) }

    // Tick down the blocking pause delay once per second until unlocked
    LaunchedEffect(packageName, promptId, sanitizedDelay) {
        if (sanitizedDelay > 0) {
            var remaining = sanitizedDelay
            remainingDelaySeconds = remaining
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1
                remainingDelaySeconds = remaining
            }
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Use a near-opaque blocking backdrop while counting down so the user cannot watch the app behind it
    val backdropColor by animateColorAsState(
        targetValue = if (isWaiting) {
            Color(0xFF060A13).copy(alpha = 0.97f)
        } else {
            Color.Black.copy(alpha = 0.72f)
        },
        animationSpec = tween(durationMillis = 260),
        label = "overlay_backdrop_color"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Full-screen click-blocking backdrop behind the card so TalkBack/semantics stay unmerged
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backdropColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* prevent pass-through click */ }
        )

        Card(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.92f)
                .testTag("overlay_prompt_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF131D31)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.verticalGradient(
                    colors = if (isWaiting) {
                        listOf(
                            Color(0xFFF59E0B).copy(alpha = 0.75f),
                            CyanPrimary.copy(alpha = 0.35f)
                        )
                    } else {
                        listOf(
                            CyanPrimary.copy(alpha = 0.6f),
                            CoralAlert.copy(alpha = 0.3f)
                        )
                    }
                ),
                width = 1.5.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon - compact 40dp circle with 22dp icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = when {
                                    isWaiting -> listOf(Color(0xFFF59E0B), Color(0xFFB45309))
                                    isRelaunch -> listOf(IndigoSecondary, Color(0xFF312E81))
                                    else -> listOf(CoralAlert, Color(0xFF9F1239))
                                }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRelaunch && !isWaiting) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Limit Alert",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_timer),
                            contentDescription = "Limit Alert",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Compact Title
                Text(
                    text = if (isRelaunch) "$appName Limit Expired" else "$appName Limit Reached",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Central Red Stat Block: Prominent bold red used time with Daily limit below
                val formattedUsed = formatCompactDuration(totalUsedSeconds, baselineMinutes)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E2235),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(CoralAlert.copy(alpha = 0.45f), Color.Transparent)
                        ),
                        width = 1.dp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Used: $formattedUsed",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFEF4444),
                                fontSize = 26.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Daily limit: ${baselineMinutes}m",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Slim countdown progress bar during pause delay
                if (isWaiting && sanitizedDelay > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val targetProgress = (sanitizedDelay - remainingDelaySeconds).toFloat() / sanitizedDelay.toFloat()
                    val animatedProgress by animateFloatAsState(
                        targetValue = targetProgress.coerceIn(0f, 1f),
                        animationSpec = tween(durationMillis = 350),
                        label = "pause_delay_progress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .testTag("pause_delay_progress_bar"),
                        color = Color(0xFFF59E0B),
                        trackColor = Color(0xFF1E293B)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions: Side-by-side horizontal row to minimize height on rotated screens
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCloseApp,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("close_app_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CoralAlert
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.horizontalGradient(listOf(CoralAlert, CoralAlert.copy(0.6f)))
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Close",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Button(
                        onClick = {
                            if (isWaiting) return@Button
                            val nextTaps = tapsCompleted + 1
                            if (nextTaps >= requiredPresses) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddFiveMinutes()
                            } else {
                                tapsCompleted = nextTaps
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch {
                                    extendButtonScale.snapTo(0.94f)
                                    extendButtonScale.animateTo(
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = 0.55f,
                                            stiffness = 500f
                                        )
                                    )
                                }
                            }
                        },
                        enabled = !isWaiting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .scale(extendButtonScale.value)
                            .testTag("add_five_minutes_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldSuccess,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF23304A),
                            disabledContentColor = Color(0xFFFBBF24)
                        )
                    ) {
                        Text(
                            text = if (isWaiting) "Wait ${remainingDelaySeconds}s" else "+5 Minutes",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Dynamic friction caption below buttons
                if (isWaiting) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val pauseHint = formatPauseDelayMessage(
                        remainingSeconds = remainingDelaySeconds,
                        multiPressEnabled = multiPressEnabled,
                        requiredPresses = requiredPresses
                    )
                    Text(
                        text = pauseHint,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pause_delay_hint")
                    )
                } else if (requiredPresses >= 2) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val pressHint = formatExtensionPressMessage(
                        requiredPresses = requiredPresses,
                        tapsCompleted = tapsCompleted
                    )
                    AnimatedContent(
                        targetState = pressHint,
                        transitionSpec = {
                            fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                        },
                        label = "extension_press_hint_transition"
                    ) { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (tapsCompleted > 0) CyanLight else Color(0xFFFBBF24),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("extension_press_hint")
                        )
                    }
                }
            }
        }
    }
}

internal fun formatPauseDelayMessage(
    remainingSeconds: Int,
    multiPressEnabled: Boolean,
    requiredPresses: Int
): String {
    val safeSec = remainingSeconds.coerceAtLeast(1)
    return if (multiPressEnabled && requiredPresses >= 2) {
        val countText = pressCountWord(requiredPresses)
        "Pause active • ${safeSec}s left, then press $countText times"
    } else {
        "Take a brief pause • Unlocks in ${safeSec}s"
    }
}

internal fun formatExtensionPressMessage(
    requiredPresses: Int,
    tapsCompleted: Int
): String {
    if (tapsCompleted <= 0) {
        val countText = pressCountWord(requiredPresses)
        return "Extension #$requiredPresses • Press $countText times to extend"
    }
    val remaining = (requiredPresses - tapsCompleted).coerceAtLeast(1)
    return if (remaining == 1) {
        "Press 1 more time to extend ($tapsCompleted/$requiredPresses)"
    } else {
        "Press $remaining more times to extend ($tapsCompleted/$requiredPresses)"
    }
}

private fun pressCountWord(count: Int): String = when (count) {
    2 -> "two"
    3 -> "three"
    else -> count.toString()
}

private fun formatCompactDuration(totalSeconds: Long, fallbackMinutes: Int): String {
    val effectiveSeconds = if (totalSeconds > 0L) totalSeconds else fallbackMinutes * 60L
    val totalMinutes = effectiveSeconds / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L

    return if (hours > 0L) {
        if (minutes > 0L) "${hours}h ${minutes}m" else "${hours}h"
    } else {
        "${totalMinutes}m"
    }
}
