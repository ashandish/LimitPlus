package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.monitor.SessionMonitorScreen
import com.example.ui.overlay.OverlayPromptCard
import com.example.ui.permissions.PermissionsScreen
import com.example.ui.simulator.TestLabScreen
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanLight
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LimitPlusTheme
import com.example.ui.viewmodel.LimitPlusViewModel

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LimitPlusTheme {
                val viewModel: LimitPlusViewModel = viewModel()

                // Notification permission launcher for Android 13+
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {
                    viewModel.checkPermissions()
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val apps by viewModel.appsList.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()
                val sessionState by viewModel.activeSessionState.collectAsState()
                val permissions by viewModel.permissions.collectAsState()
                val timeUntilMidnight by viewModel.timeUntilMidnight.collectAsState()
                val recentLogs by viewModel.recentLogs.collectAsState()
                val promptData by viewModel.currentPrompt.collectAsState()

                var selectedTab by remember { mutableIntStateOf(0) }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CyanPrimary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "+5",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFF04202C),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "LimitPlus",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (sessionState.isUnderActiveExtension) EmeraldSuccess.copy(0.2f) else CyanPrimary.copy(0.2f)
                                        ) {
                                            Text(
                                                text = if (sessionState.isUnderActiveExtension) "+5m Active" else "Protected",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (sessionState.isUnderActiveExtension) EmeraldSuccess else CyanLight,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.5.sp
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = Color(0xFF0B111E)
                                )
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = Color(0xFF131D31),
                                contentColor = Color.White
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.Apps, contentDescription = "App Limits") },
                                    label = { Text("App Limits", fontWeight = FontWeight.SemiBold) },
                                    modifier = Modifier.testTag("nav_apps"),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CyanLight,
                                        selectedTextColor = CyanLight,
                                        indicatorColor = CyanPrimary.copy(alpha = 0.25f),
                                        unselectedIconColor = Color(0xFF94A3B8),
                                        unselectedTextColor = Color(0xFF94A3B8)
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.Timer, contentDescription = "Live Monitor") },
                                    label = { Text("Monitor", fontWeight = FontWeight.SemiBold) },
                                    modifier = Modifier.testTag("nav_monitor"),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CyanLight,
                                        selectedTextColor = CyanLight,
                                        indicatorColor = CyanPrimary.copy(alpha = 0.25f),
                                        unselectedIconColor = Color(0xFF94A3B8),
                                        unselectedTextColor = Color(0xFF94A3B8)
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Default.Science, contentDescription = "Simulator") },
                                    label = { Text("Simulator", fontWeight = FontWeight.SemiBold) },
                                    modifier = Modifier.testTag("nav_simulator"),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CyanLight,
                                        selectedTextColor = CyanLight,
                                        indicatorColor = CyanPrimary.copy(alpha = 0.25f),
                                        unselectedIconColor = Color(0xFF94A3B8),
                                        unselectedTextColor = Color(0xFF94A3B8)
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = { selectedTab = 3 },
                                    icon = { Icon(Icons.Default.Security, contentDescription = "Permissions") },
                                    label = { Text("Permissions", fontWeight = FontWeight.SemiBold) },
                                    modifier = Modifier.testTag("nav_permissions"),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CyanLight,
                                        selectedTextColor = CyanLight,
                                        indicatorColor = CyanPrimary.copy(alpha = 0.25f),
                                        unselectedIconColor = Color(0xFF94A3B8),
                                        unselectedTextColor = Color(0xFF94A3B8)
                                    )
                                )
                            }
                        },
                        containerColor = Color(0xFF0B111E)
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (selectedTab) {
                                0 -> DashboardScreen(
                                    apps = apps,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = viewModel::setSearchQuery,
                                    onToggleMonitored = viewModel::toggleMonitored,
                                    onUpdateBaseline = viewModel::updateBaseline,
                                    onSimulateSession = { pkg ->
                                        selectedTab = 2 // Switch to simulator
                                        viewModel.simulateForegroundApp(pkg)
                                    }
                                )
                                1 -> SessionMonitorScreen(
                                    sessionState = sessionState,
                                    timeUntilMidnight = timeUntilMidnight,
                                    recentLogs = recentLogs,
                                    onGrantExtension = viewModel::grantExtension,
                                    onCloseApp = viewModel::closeTargetApp,
                                    onResetTodayUsage = viewModel::resetTodayUsage
                                )
                                2 -> TestLabScreen(
                                    apps = apps,
                                    onSimulateForeground = viewModel::simulateForegroundApp,
                                    onSimulateAddSeconds = viewModel::simulateAddUsageSeconds,
                                    onSimulateExitToHome = viewModel::simulateExitToHome,
                                    onTriggerPrompt = viewModel::triggerTestPrompt
                                )
                                3 -> PermissionsScreen(
                                    permissions = permissions,
                                    onRequestNotificationPermission = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Floating High-Priority Modal Overlay Dialog (in-app interactive prompt)
                    promptData?.let { prompt ->
                        OverlayPromptCard(
                            packageName = prompt.packageName,
                            appName = prompt.appName,
                            baselineMinutes = prompt.baselineMinutes,
                            isRelaunch = prompt.isRelaunchAfterExhaustion,
                            onAddFiveMinutes = {
                                viewModel.grantExtension(prompt.packageName, prompt.appName)
                            },
                            onCloseApp = {
                                viewModel.closeTargetApp(prompt.packageName)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
