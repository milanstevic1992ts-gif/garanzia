package com.milanstevic.garanzia.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class PremiumDestination {
    HOME,
    ARCHIVE,
    STORAGE,
}

@Composable
fun PremiumBottomBar(
    selected: PremiumDestination,
    onHome: () -> Unit,
    onArchive: () -> Unit,
    onStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
    ) {
        NavigationBarItem(
            selected = selected == PremiumDestination.HOME,
            onClick = onHome,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = "Home",
                )
            },
            label = { Text("Home") },
            colors = premiumNavigationColors(),
        )

        NavigationBarItem(
            selected = selected == PremiumDestination.ARCHIVE,
            onClick = onArchive,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = "Archivio",
                )
            },
            label = { Text("Archivio") },
            colors = premiumNavigationColors(),
        )

        NavigationBarItem(
            selected = selected == PremiumDestination.STORAGE,
            onClick = onStorage,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CloudSync,
                    contentDescription = "Archiviazione",
                )
            },
            label = { Text("Backup") },
            colors = premiumNavigationColors(),
        )
    }
}

@Composable
private fun premiumNavigationColors() =
    NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
