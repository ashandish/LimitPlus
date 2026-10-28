package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.InstalledAppItem
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanLight
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoLight
import com.example.ui.theme.IndigoSecondary

@Composable
fun DashboardScreen(
    apps: List<InstalledAppItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleMonitored: (String, Boolean) -> Unit,
    onUpdateBaseline: (String, Int) -> Unit,
    hasMissingPermissions: Boolean = false,
    onNavigateToPermissions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedAppForBaseline by remember { mutableStateOf<InstalledAppItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        if (hasMissingPermissions) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPermissions() }
                    .testTag("permissions_warning_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CoralAlert.copy(alpha = 0.15f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(CoralAlert, CoralAlert.copy(0.4f))),
                    width = 1.dp
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = CoralAlert,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Permissions Setup Required",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Tap to grant Usage Access & Draw Over Apps for full background protection.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFFDA4AF),
                                fontSize = 11.sp
                            )
                        )
                    }
                    Text(
                        text = "Enable",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = CoralAlert
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Search Bar (full-width)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_search_field"),
            placeholder = { Text("Search installed apps...", color = Color(0xFF64748B)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = CyanPrimary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = Color(0xFF28385A),
                focusedContainerColor = Color(0xFF131D31),
                unfocusedContainerColor = Color(0xFF131D31),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Metrics Summary Row
        val monitoredCount = apps.count { it.isMonitored }
        val exhaustedCount = apps.count { it.isMonitored && it.isBaselineExhausted }
        val extensionsCount = apps.sumOf { it.extensionsCountToday }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryMetricCard(
                title = "Monitored",
                value = "$monitoredCount",
                accentColor = CyanPrimary,
                modifier = Modifier.weight(1f)
            )
            SummaryMetricCard(
                title = "Exhausted",
                value = "$exhaustedCount",
                accentColor = if (exhaustedCount > 0) CoralAlert else IndigoLight,
                modifier = Modifier.weight(1f)
            )
            SummaryMetricCard(
                title = "+5m Added",
                value = "$extensionsCount",
                accentColor = EmeraldSuccess,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Apps List
        if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No apps match '$searchQuery'",
                        style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF94A3B8))
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("apps_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppLimitCard(
                        app = app,
                        onToggle = { isChecked -> onToggleMonitored(app.packageName, isChecked) },
                        onEditBaseline = { selectedAppForBaseline = app }
                    )
                }
            }
        }
    }

    // Baseline Limit Picker Dialog
    selectedAppForBaseline?.let { app ->
        BaselineLimitDialog(
            app = app,
            onDismiss = { selectedAppForBaseline = null },
            onConfirm = { newBaseline ->
                onUpdateBaseline(app.packageName, newBaseline)
                selectedAppForBaseline = null
            }
        )
    }
}

@Composable
fun SummaryMetricCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(listOf(accentColor.copy(0.4f), Color.Transparent)),
            width = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            )
        }
    }
}

@Composable
fun AppLimitCard(
    app: InstalledAppItem,
    onToggle: (Boolean) -> Unit,
    onEditBaseline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baselineSeconds = app.baselineMinutes * 60L
    val progress = (app.todayBaselineUsedSeconds.toFloat() / baselineSeconds.toFloat()).coerceIn(0f, 1f)
    val usedMinutes = app.todayBaselineUsedSeconds / 60L
    val overtimeMinutes = app.todayOvertimeUsedSeconds / 60L

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_card_${app.packageName}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isCurrentlyActive) Color(0xFF18233C) else Color(0xFF131D31)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (app.isCurrentlyActive) {
                Brush.horizontalGradient(listOf(CyanPrimary, IndigoSecondary))
            } else if (app.isMonitored && app.isBaselineExhausted) {
                Brush.horizontalGradient(listOf(CoralAlert.copy(0.5f), Color.Transparent))
            } else {
                Brush.horizontalGradient(listOf(Color(0xFF28385A), Color(0xFF1B2844)))
            },
            width = if (app.isCurrentlyActive) 1.5.dp else 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Avatar
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(CyanPrimary.copy(0.2f), IndigoSecondary.copy(0.3f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.appName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyanLight
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // App Name & Package
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (app.isCurrentlyActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CyanPrimary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = CyanLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Switch
                Switch(
                    checked = app.isMonitored,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("toggle_${app.packageName}"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CyanPrimary,
                        uncheckedTrackColor = Color(0xFF1E2942),
                        uncheckedThumbColor = Color(0xFF94A3B8)
                    )
                )
            }

            AnimatedVisibility(visible = app.isMonitored) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    // Baseline and Status row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Immutable Baseline Chip
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E2942),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(CyanPrimary.copy(0.3f), Color.Transparent)),
                                width = 1.dp
                            ),
                            modifier = Modifier.clickable { onEditBaseline() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Daily Baseline: ${app.baselineMinutes}m (Tap to edit)",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color(0xFFE2E8F0),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        // Usage info
                        Text(
                            text = "${usedMinutes}m / ${app.baselineMinutes}m",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (app.isBaselineExhausted) CoralAlert else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (app.isBaselineExhausted) CoralAlert else CyanPrimary,
                        trackColor = Color(0xFF1E2942)
                    )

                    // Overtime & Extension Badge if any
                    if (app.todayOvertimeUsedSeconds > 0 || app.extensionsCountToday > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EmeraldSuccess.copy(alpha = 0.15f),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = Brush.horizontalGradient(listOf(EmeraldSuccess.copy(0.4f), Color.Transparent)),
                                    width = 1.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "+5m Overtime: ${overtimeMinutes}m (${app.extensionsCountToday} extensions)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = EmeraldSuccess,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }

                            Text(
                                text = "Baseline untouched 🔒",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BaselineLimitDialog(
    app: InstalledAppItem,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(app.baselineMinutes) }
    val presets = listOf(5, 10, 15, 20, 25, 30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Baseline for ${app.appName}",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        },
        text = {
            Column {
                Text(
                    text = "This daily baseline is strictly immutable. Temporary +5m extensions will never permanently increase this value.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Selected Quota: $selectedMinutes minutes / day",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = CyanLight,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Presets Wrap
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rows = presets.chunked(4)
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { mins ->
                                FilterChip(
                                    selected = selectedMinutes == mins,
                                    onClick = { selectedMinutes = mins },
                                    label = { Text("${mins}m") },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF1E2942),
                                        labelColor = Color(0xFFCBD5E1)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedMinutes) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
            ) {
                Text("Save Baseline", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF131D31),
        shape = RoundedCornerShape(24.dp)
    )
}
