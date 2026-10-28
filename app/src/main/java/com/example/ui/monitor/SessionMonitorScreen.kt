package com.example.ui.monitor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    sessionState: ActiveSessionUiState = ActiveSessionUiState(),
    timeUntilMidnight: String,
    recentLogs: List<ExtensionSessionLog>,
    onGrantExtension: (String, String) -> Unit = { _, _ -> },
    onCloseApp: (String) -> Unit = {},
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
        // Daily Midnight Reset Information Card
        item {
            MidnightResetCard(
                timeUntilMidnight = timeUntilMidnight
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
                            text = "When a monitored app limit expires, temporary extensions granted from the overlay or notification appear here.",
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
fun MidnightResetCard(
    timeUntilMidnight: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("midnight_reset_card"),
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
                    text = "Automated Midnight Reset",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$timeUntilMidnight until automatic reset at 00:00. Baselines remain strictly preserved, and manual resets are locked.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmeraldSuccess.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Automated and locked",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Auto",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = EmeraldSuccess,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
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
