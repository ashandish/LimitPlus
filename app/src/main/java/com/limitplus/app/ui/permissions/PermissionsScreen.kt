package com.limitplus.app.ui.permissions

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limitplus.app.BuildConfig
import com.limitplus.app.R
import com.limitplus.app.data.preferences.FrictionSettings
import com.limitplus.app.ui.theme.CoralAlert
import com.limitplus.app.ui.theme.CyanLight
import com.limitplus.app.ui.theme.CyanPrimary
import com.limitplus.app.ui.theme.EmeraldSuccess
import com.limitplus.app.ui.viewmodel.PermissionsStatus
import kotlin.math.roundToInt

private val AmberAccent = Color(0xFFF59E0B)

@Composable
fun PermissionsScreen(
    permissions: PermissionsStatus,
    frictionSettings: FrictionSettings = FrictionSettings(),
    onMultiPressToggle: (Boolean) -> Unit = {},
    onPauseDelayToggle: (Boolean) -> Unit = {},
    onPauseDelaySecondsChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    onRequestNotificationPermission: (() -> Unit)? = null,
    versionName: String = BuildConfig.VERSION_NAME,
    versionCode: Int = BuildConfig.VERSION_CODE
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp)
    ) {
        // Section 1: Extension Friction Settings
        item {
            Column(modifier = Modifier.padding(bottom = 2.dp)) {
                Text(
                    text = "Extension Friction",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose how LimitPlus slows down +5 minute extensions across all monitored apps. Use either method or stack both together.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF94A3B8),
                        lineHeight = 20.sp
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                ActiveFrictionSummaryBadge(frictionSettings = frictionSettings)
            }
        }

        // Toggle 1: Escalating Multi-Press
        item {
            FrictionToggleCard(
                title = "Escalating Multi-Press",
                description = "Requires more taps on +5 Minutes with each extension today (1st = 1 tap, 2nd = 2 taps, 3rd = 3 taps).",
                iconRes = R.drawable.ic_touch_app,
                accentColor = CyanPrimary,
                checked = frictionSettings.isMultiPressEnabled,
                switchTag = "switch_multi_press",
                onCheckedChange = onMultiPressToggle
            )
        }

        // Toggle 2: Configurable Pause Delay Screen
        item {
            PauseDelaySettingsCard(
                isEnabled = frictionSettings.isPauseDelayEnabled,
                delaySeconds = frictionSettings.pauseDelaySeconds,
                onToggle = onPauseDelayToggle,
                onDelaySecondsChange = onPauseDelaySecondsChange
            )
        }

        // Section 2: App Permissions
        item {
            Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                Text(
                    text = "App Permissions",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enable the permissions below so LimitPlus can track screen time and display limit alerts.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF94A3B8),
                        lineHeight = 20.sp
                    )
                )
            }
        }

        // 1. Usage Access
        item {
            PermissionItemCard(
                title = "Usage Access",
                description = "Detects which app is in use to track elapsed daily screen time.",
                isGranted = permissions.hasUsageAccess,
                iconRes = R.drawable.ic_hourglass_top,
                actionLabel = "Enable Access",
                onAction = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )
        }

        // 2. Draw Over Other Apps
        item {
            PermissionItemCard(
                title = "Display Over Other Apps",
                description = "Displays the +5 minute alert over target apps when a limit is reached.",
                isGranted = permissions.hasOverlayPermission,
                iconRes = R.drawable.ic_layers,
                actionLabel = "Enable Overlay",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                }
            )
        }

        // 3. Ignore Battery Optimization
        item {
            PermissionItemCard(
                title = "Background Activity",
                description = "Prevents Android from stopping LimitPlus when your device is idle.",
                isGranted = permissions.hasBatteryOptimizationIgnored,
                iconRes = R.drawable.ic_battery_alert,
                actionLabel = "Enable Background",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(fallback)
                        }
                    }
                }
            )
        }

        // 4. Notifications
        item {
            PermissionItemCard(
                title = "Notifications",
                description = "Required to keep background monitoring running reliably and alert you when limits are reached.",
                isGranted = permissions.hasNotificationPermission,
                iconRes = R.drawable.ic_notifications,
                actionLabel = "Enable Notifications",
                onAction = {
                    if (onRequestNotificationPermission != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        onRequestNotificationPermission()
                    } else {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(fallback)
                        }
                    }
                }
            )
        }

        // App Version Footer
        item {
            SettingsVersionFooter(
                versionName = versionName,
                versionCode = versionCode
            )
        }
    }
}

@Composable
private fun SettingsVersionFooter(
    versionName: String,
    versionCode: Int
) {
    val appName = stringResource(R.string.app_name)
    val versionLabel = stringResource(R.string.settings_version_footer, versionName, versionCode)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFF131D31),
            border = BorderStroke(1.dp, Color(0xFF28385A)),
            modifier = Modifier.testTag("settings_version_footer")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(CyanPrimary)
                )
                Text(
                    text = appName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF475569)
                    )
                )
                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.testTag("settings_version_text")
                )
            }
        }
    }
}

@Composable
private fun ActiveFrictionSummaryBadge(frictionSettings: FrictionSettings) {
    val badgeColor = when {
        frictionSettings.isPauseDelayEnabled && frictionSettings.isMultiPressEnabled -> EmeraldSuccess
        frictionSettings.isPauseDelayEnabled -> AmberAccent
        frictionSettings.isMultiPressEnabled -> CyanLight
        else -> Color(0xFF94A3B8)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = badgeColor.copy(alpha = 0.14f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(badgeColor.copy(alpha = 0.45f), Color.Transparent)),
            width = 1.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("friction_mode_summary")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tune),
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Active mode: ${frictionSettings.activeModeSummary()}",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
private fun FrictionToggleCard(
    title: String,
    description: String,
    iconRes: Int,
    accentColor: Color,
    checked: Boolean,
    switchTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (checked) {
                Brush.horizontalGradient(listOf(accentColor.copy(0.45f), Color.Transparent))
            } else {
                Brush.horizontalGradient(listOf(Color(0xFF28385A), Color(0xFF1E2942)))
            },
            width = 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (checked) accentColor.copy(alpha = 0.18f)
                            else Color(0xFF1E293B)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (checked) accentColor else Color(0xFF94A3B8),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )

                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.testTag(switchTag),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor,
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF1E293B),
                        uncheckedBorderColor = Color(0xFF334155)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp
                )
            )
        }
    }
}

@Composable
private fun PauseDelaySettingsCard(
    isEnabled: Boolean,
    delaySeconds: Int,
    onToggle: (Boolean) -> Unit,
    onDelaySecondsChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (isEnabled) {
                Brush.horizontalGradient(listOf(AmberAccent.copy(0.55f), Color.Transparent))
            } else {
                Brush.horizontalGradient(listOf(Color(0xFF28385A), Color(0xFF1E2942)))
            },
            width = 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isEnabled) AmberAccent.copy(alpha = 0.18f)
                            else Color(0xFF1E293B)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_timer),
                        contentDescription = null,
                        tint = if (isEnabled) AmberAccent else Color(0xFF94A3B8),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Pause Delay Screen",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("switch_pause_delay"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AmberAccent,
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF1E293B),
                        uncheckedBorderColor = Color(0xFF334155)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Blocks the monitored app with a countdown screen before +5 Minutes unlocks. If Multi-Press is also active, the countdown runs first.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp
                )
            )

            AnimatedVisibility(
                visible = isEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .testTag("pause_delay_config_panel")
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0E1626),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Delay Duration",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AmberAccent.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = "${delaySeconds}s",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = AmberAccent,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                            .testTag("pause_delay_value")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick Preset Pills (clean centered layout for all screen widths)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FrictionSettings.PRESET_DELAY_OPTIONS.forEach { preset ->
                                    val selected = delaySeconds == preset
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) AmberAccent.copy(alpha = 0.25f) else Color(0xFF162033),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (selected) AmberAccent else Color(0xFF28385A)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onDelaySecondsChange(preset) }
                                            .testTag("delay_preset_$preset")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${preset}s",
                                                maxLines = 1,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = if (selected) Color.White else Color(0xFF94A3B8),
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Fine-adjustment stepper & slider (3s to 120s)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = { onDelaySecondsChange(delaySeconds - 1) },
                                    enabled = delaySeconds > FrictionSettings.MIN_PAUSE_DELAY_SECONDS,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("delay_decrease_button"),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = Color(0xFF1A2740),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_remove),
                                        contentDescription = "Decrease delay by 1 second",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Slider(
                                    value = delaySeconds.toFloat(),
                                    onValueChange = { onDelaySecondsChange(it.roundToInt()) },
                                    valueRange = FrictionSettings.MIN_PAUSE_DELAY_SECONDS.toFloat()..FrictionSettings.MAX_PAUSE_DELAY_SECONDS.toFloat(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("delay_slider"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = AmberAccent,
                                        activeTrackColor = AmberAccent,
                                        inactiveTrackColor = Color(0xFF1E293B)
                                    )
                                )

                                FilledTonalIconButton(
                                    onClick = { onDelaySecondsChange(delaySeconds + 1) },
                                    enabled = delaySeconds < FrictionSettings.MAX_PAUSE_DELAY_SECONDS,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("delay_increase_button"),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = Color(0xFF1A2740),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase delay by 1 second",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItemCard(
    title: String,
    description: String,
    isGranted: Boolean,
    iconRes: Int,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (isGranted) {
                Brush.horizontalGradient(listOf(EmeraldSuccess.copy(0.35f), Color.Transparent))
            } else {
                Brush.horizontalGradient(listOf(Color(0xFF28385A), Color(0xFF1E2942)))
            },
            width = 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGranted) EmeraldSuccess.copy(0.15f)
                            else CyanPrimary.copy(0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (isGranted) EmeraldSuccess else CyanLight,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGranted) EmeraldSuccess.copy(0.18f) else CoralAlert.copy(0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isGranted) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check_circle),
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = CoralAlert,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isGranted) "Enabled" else "Required",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isGranted) EmeraldSuccess else CoralAlert,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp
                )
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}
