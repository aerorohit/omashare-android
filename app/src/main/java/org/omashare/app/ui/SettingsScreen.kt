package org.omashare.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    versionName: String,
    onThemeSelected: (AppTheme) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MochaBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MochaText,
                )
            }
            Text(
                text = "Settings",
                color = MochaText,
                fontSize = 22.sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SettingsSectionHeader("Theme")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MochaSurface, OmashareCardShape)
                    .padding(vertical = 4.dp),
            ) {
                AppTheme.entries.forEach { theme ->
                    ThemeOptionRow(
                        theme = theme,
                        selected = theme == currentTheme,
                        onSelect = { onThemeSelected(theme) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingsSectionHeader("About")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MochaSurface, OmashareCardShape)
                    .padding(16.dp),
            ) {
                Text(
                    text = "Omashare",
                    color = MochaText,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Version $versionName",
                    color = MochaMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Share files between your devices over the local network. " +
                        "Built on Iroh for fast, encrypted transfers.",
                    color = MochaMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = MochaMuted,
        fontSize = 13.sp,
    )
}

@Composable
private fun ThemeOptionRow(
    theme: AppTheme,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val palette = theme.palette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = MochaAccent,
                unselectedColor = MochaMuted,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.label,
                color = if (selected) MochaAccent else MochaText,
                fontSize = 16.sp,
            )
            Text(
                text = "Editor-style dark theme",
                color = MochaMuted,
                fontSize = 12.sp,
            )
        }
        ThemeSwatch(palette = palette)
    }
}

@Composable
private fun ThemeSwatch(palette: OmasharePalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SwatchDot(palette.background)
        SwatchDot(palette.surface)
        SwatchDot(palette.accent)
        SwatchDot(palette.green)
    }
}

@Composable
private fun SwatchDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MochaOverlay, CircleShape),
    )
}
