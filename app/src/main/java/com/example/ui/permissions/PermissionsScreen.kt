package com.example.ui.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanLight
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoLight
import com.example.ui.theme.IndigoSecondary
import com.example.ui.viewmodel.PermissionsStatus

@Composable
fun PermissionsScreen(
    permissions: PermissionsStatus,
    modifier: Modifier = Modifier,
    onRequestNotificationPermission: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val grantedCount = listOf(
        permissions.hasUsageAccess,
        permissions.hasOverlayPermission,
        permissions.hasBatteryOptimizationIgnored
    ).count { it }
    val allPermissionsGranted = grantedCount == 3
    val progressFraction = grantedCount / 3f

    val onGrantAllAction: () -> Unit = {
        when {
            !permissions.hasUsageAccess -> {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
            !permissions.hasOverlayPermission -> {
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
            !permissions.hasBatteryOptimizationIgnored -> {
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
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("permissions_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        // Dedicated "Grant All Permissions" Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_all_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        if (allPermissionsGranted) {
                            listOf(EmeraldSuccess, EmeraldSuccess.copy(0.4f))
                        } else {
                            listOf(CyanPrimary, IndigoSecondary)
                        }
                    ),
                    width = 1.5.dp
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (allPermissionsGranted) EmeraldSuccess.copy(0.2f)
                                    else CyanPrimary.copy(0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (allPermissionsGranted) Icons.Default.DoneAll else Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (allPermissionsGranted) EmeraldSuccess else CyanLight,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (allPermissionsGranted) "Full Protection Enabled" else "Grant All Permissions",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (allPermissionsGranted) {
                                    "All 3 system permissions are active. Background enforcement is ready."
                                } else {
                                    "$grantedCount of 3 permissions granted. Tap below to complete setup."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (allPermissionsGranted) EmeraldSuccess else Color(0xFF94A3B8),
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (allPermissionsGranted) EmeraldSuccess else CyanPrimary,
                        trackColor = Color(0xFF1E2942)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // "Grant All Permissions" Primary Action Button
                    Button(
                        onClick = onGrantAllAction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("grant_all_permissions_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allPermissionsGranted) EmeraldSuccess.copy(0.25f) else CyanPrimary,
                            contentColor = if (allPermissionsGranted) EmeraldSuccess else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (allPermissionsGranted) Icons.Default.CheckCircle else Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (allPermissionsGranted) {
                                "All Permissions Granted ✓"
                            } else {
                                "Grant All Permissions (1-Tap Setup)"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // 1. Usage Access
        item {
            PermissionItemCard(
                title = "1. Usage Access (PACKAGE_USAGE_STATS)",
                description = "Required by Android to detect which app is active in foreground and track elapsed screen time against daily baselines.",
                isGranted = permissions.hasUsageAccess,
                icon = Icons.Default.Shield,
                actionLabel = "Grant Usage Access",
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
                title = "2. Display Over Other Apps (SYSTEM_ALERT_WINDOW)",
                description = "Required to display the high-priority '+5 Minute' extension dialogue over target apps without interrupting system navigation.",
                isGranted = permissions.hasOverlayPermission,
                icon = Icons.Default.Layers,
                actionLabel = "Grant Display Over Apps",
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
                title = "3. Background Battery Persistence",
                description = "Exempts LimitPlus from Android Doze mode and aggressive OEM background app killing so limits trigger without delay.",
                isGranted = permissions.hasBatteryOptimizationIgnored,
                icon = Icons.Default.BatteryAlert,
                actionLabel = "Grant Background Access",
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
    }
}

@Composable
fun PermissionItemCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (isGranted) {
                Brush.horizontalGradient(listOf(EmeraldSuccess.copy(0.3f), Color.Transparent))
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGranted) EmeraldSuccess.copy(0.15f)
                            else CyanPrimary.copy(0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) EmeraldSuccess else CyanLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGranted) EmeraldSuccess.copy(0.2f) else CoralAlert.copy(0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isGranted) EmeraldSuccess else CoralAlert,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isGranted) "Granted" else "Required",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isGranted) EmeraldSuccess else CoralAlert,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF94A3B8),
                    lineHeight = 16.sp
                )
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}
