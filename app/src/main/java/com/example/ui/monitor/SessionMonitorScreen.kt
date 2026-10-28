package com.example.ui.monitor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ExtensionSessionLog
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanLight
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoLight
import com.example.ui.theme.IndigoSecondary
import com.example.ui.viewmodel.ActiveSessionUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionMonitorScreen(
    sessionState: ActiveSessionUiState,
    timeUntilMidnight: String,
    recentLogs: List<ExtensionSessionLog>,
    onGrantExtension: (String, String) -> Unit,
    onCloseApp: (String) -> Unit,
    onResetTodayUsage: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("session_monitor_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        // Active Session Hero Card
        item {
            ActiveSessionHeroCard(
                sessionState = sessionState,
                onGrantExtension = onGrantExtension,
                onCloseApp = onCloseApp
            )
        }

        // Daily Midnight Reset Information Card
        item {
            MidnightResetCard(
                timeUntilMidnight = timeUntilMidnight,
                onManualReset = onResetTodayUsage
            )
        }

        // Section Title: Audit Log of Temporary Extensions
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Today's Extension Audit Trail",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }

        if (recentLogs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF131D31),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No temporary extensions requested yet today.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF94A3B8)),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "When an app reaches its baseline limit, grant +5m to see live session logs.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B)),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(recentLogs, key = { it.id }) { log ->
                ExtensionLogItem(log = log)
            }
        }
    }
}

@Composable
fun ActiveSessionHeroCard(
    sessionState: ActiveSessionUiState,
    onGrantExtension: (String, String) -> Unit,
    onCloseApp: (String) -> Unit
) {
    val isAppActive = sessionState.foregroundPackage != null
    val isUnderExt = sessionState.isUnderActiveExtension
    val remSec = sessionState.extensionRemainingSeconds
    val mins = remSec / 60
    val secs = remSec % 60
    val formattedCountdown = String.format("%02d:%02d", mins, secs)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isUnderExt) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_session_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                colors = if (isUnderExt) {
                    listOf(EmeraldSuccess, EmeraldSuccess.copy(0.3f))
                } else if (sessionState.isBaselineExhausted) {
                    listOf(CoralAlert, CoralAlert.copy(0.3f))
                } else {
                    listOf(CyanPrimary, IndigoSecondary.copy(0.4f))
                }
            ),
            width = 1.5.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isUnderExt) EmeraldSuccess.copy(0.2f) else CyanPrimary.copy(0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isUnderExt) EmeraldSuccess else CyanPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isUnderExt) "TEMPORARY EXTENSION WINDOW" else "FOREGROUND STATUS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isUnderExt) EmeraldSuccess else CyanLight,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Text(
                    text = "Immutable Baseline",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // App Name or Idle
            Text(
                text = sessionState.foregroundAppName ?: "No Target App in Foreground",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            Text(
                text = if (sessionState.isMonitored) {
                    "Configured Daily Baseline: ${sessionState.baselineMinutes} minutes"
                } else {
                    "Open a monitored app or launch one from the Test Lab"
                },
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF94A3B8))
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Visual Distinction: Baseline Used vs Overtime
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1B2844),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Baseline Used",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${sessionState.baselineUsedSeconds / 60}m / ${sessionState.baselineMinutes}m",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (sessionState.isBaselineExhausted) CoralAlert else CyanLight
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(Color(0xFF28385A))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Temporary Overtime",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+${sessionState.overtimeUsedSeconds / 60}m",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess
                            )
                        )
                    }
                }
            }

            // Extension countdown timer if active
            AnimatedVisibility(visible = isUnderExt) {
                Column(
                    modifier = Modifier.padding(top = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0F172A),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(EmeraldSuccess, CyanPrimary)),
                            width = 1.dp
                        ),
                        modifier = Modifier.scale(pulseScale)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "$formattedCountdown remaining in +5m extension",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "⚡ Exiting the app terminates this extension immediately. Daily baseline remains unaffected.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Quick Actions if app is in foreground
            if (sessionState.foregroundPackage != null && sessionState.isMonitored) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onGrantExtension(
                                sessionState.foregroundPackage,
                                sessionState.foregroundAppName ?: sessionState.foregroundPackage
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+5 Minutes", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onCloseApp(sessionState.foregroundPackage) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralAlert),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(CoralAlert, CoralAlert.copy(0.6f)))
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Close App", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun MidnightResetCard(
    timeUntilMidnight: String,
    onManualReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(IndigoSecondary.copy(0.4f), Color.Transparent)),
            width = 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(IndigoSecondary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = IndigoLight,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Midnight Reset",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$timeUntilMidnight until daily usage resets to 0. Configured baselines stay strictly intact.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onManualReset,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2942))
            ) {
                Text(
                    text = "Reset Now",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = CyanLight,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun ExtensionLogItem(log: ExtensionSessionLog) {
    val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF131D31),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(Color(0xFF28385A), Color(0xFF1E2942))),
            width = 1.dp
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when (log.status) {
                            "GRANTED" -> EmeraldSuccess.copy(alpha = 0.2f)
                            "TERMINATED_ON_EXIT" -> CoralAlert.copy(alpha = 0.2f)
                            else -> IndigoSecondary.copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (log.status) {
                        "GRANTED" -> Icons.Default.Add
                        "TERMINATED_ON_EXIT" -> Icons.Default.ExitToApp
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = when (log.status) {
                        "GRANTED" -> EmeraldSuccess
                        "TERMINATED_ON_EXIT" -> CoralAlert
                        else -> IndigoLight
                    },
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.appName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
                Text(
                    text = when (log.status) {
                        "GRANTED" -> "+5 Minute Temporary Extension Granted"
                        "TERMINATED_ON_EXIT" -> "Terminated on Exit (Baseline Preserved)"
                        "TERMINATED_USER_CLOSED" -> "App Closed (Sent to Home Screen)"
                        else -> "Session Complete"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = when (log.status) {
                            "GRANTED" -> EmeraldSuccess
                            "TERMINATED_ON_EXIT" -> CoralAlert
                            else -> Color(0xFF94A3B8)
                        },
                        fontSize = 11.sp
                    )
                )
            }

            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF64748B),
                    fontSize = 10.5.sp
                )
            )
        }
    }
}
