package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SyncLock
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SyncLock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.NavigationTab

@Composable
fun BottomNavBar(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == NavigationTab.DASHBOARD,
            onClick = { onTabSelected(NavigationTab.DASHBOARD) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == NavigationTab.DASHBOARD) {
                        Icons.Filled.Home
                    } else {
                        Icons.Outlined.Home
                    },
                    contentDescription = "Home"
                )
            },
            label = {
                Text(
                    text = "Home",
                    fontWeight = if (selectedTab == NavigationTab.DASHBOARD) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            colors = itemColors,
            modifier = Modifier.testTag("nav_dashboard")
        )

        NavigationBarItem(
            selected = selectedTab == NavigationTab.BRIDGE,
            onClick = { onTabSelected(NavigationTab.BRIDGE) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == NavigationTab.BRIDGE) {
                        Icons.Filled.SyncLock
                    } else {
                        Icons.Outlined.SyncLock
                    },
                    contentDescription = "Permissions and setup"
                )
            },
            label = {
                Text(
                    text = "Permissions",
                    fontWeight = if (selectedTab == NavigationTab.BRIDGE) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            colors = itemColors,
            modifier = Modifier.testTag("nav_bridge")
        )

        NavigationBarItem(
            selected = selectedTab == NavigationTab.REPORTS,
            onClick = { onTabSelected(NavigationTab.REPORTS) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == NavigationTab.REPORTS) {
                        Icons.Filled.History
                    } else {
                        Icons.Outlined.History
                    },
                    contentDescription = "Activity log"
                )
            },
            label = {
                Text(
                    text = "Activity",
                    fontWeight = if (selectedTab == NavigationTab.REPORTS) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            colors = itemColors,
            modifier = Modifier.testTag("nav_reports")
        )

        NavigationBarItem(
            selected = selectedTab == NavigationTab.SETTINGS,
            onClick = { onTabSelected(NavigationTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == NavigationTab.SETTINGS) {
                        Icons.Filled.Settings
                    } else {
                        Icons.Outlined.Settings
                    },
                    contentDescription = "Settings"
                )
            },
            label = {
                Text(
                    text = "Settings",
                    fontWeight = if (selectedTab == NavigationTab.SETTINGS) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            colors = itemColors,
            modifier = Modifier.testTag("nav_settings")
        )
    }
}
