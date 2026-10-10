package org.omashare.app.ui

import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.omashare.app.R

@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    versionName: String,
    onThemeSelected: (AppTheme) -> Unit,
    onAddQuickSettingsTile: () -> Unit,
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
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MochaMuted,
                )
            }
            Text(
                text = "Settings",
                color = MochaText,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Hairline()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("Theme")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MochaSurface, OmashareCardShape),
            ) {
                AppTheme.entries.forEachIndexed { index, theme ->
                    if (index > 0) Hairline()
                    ThemeOptionRow(
                        theme = theme,
                        selected = theme == currentTheme,
                        onSelect = { onThemeSelected(theme) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.settings_quick_settings))
            Spacer(Modifier.height(8.dp))
            QuickSettingsTileCard(onAddQuickSettingsTile = onAddQuickSettingsTile)

            Spacer(Modifier.height(24.dp))
            SectionLabel("About")
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
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Version $versionName",
                    color = MochaMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
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
private fun QuickSettingsTileCard(onAddQuickSettingsTile: () -> Unit) {
    val canRequestTile = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MochaSurface, OmashareCardShape)
            .then(
                if (canRequestTile) {
                    Modifier.clickable(role = Role.Button, onClick = onAddQuickSettingsTile)
                } else {
                    Modifier
                },
            )
            .padding(16.dp),
    ) {
        if (canRequestTile) {
            Text(
                text = stringResource(R.string.settings_add_qs_tile),
                color = MochaText,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_qs_tile_summary),
                color = MochaMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        } else {
            Text(
                text = stringResource(R.string.settings_qs_tile_manual),
                color = MochaMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
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
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (selected) MochaAccent else MochaMuted,
                    CircleShape,
                ),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.label,
                color = if (selected) MochaAccent else MochaText,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = themeSubtitle(theme),
                color = MochaMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        ThemeSwatch(palette = palette)
    }
}

private fun themeSubtitle(theme: AppTheme): String = when (theme) {
    AppTheme.Mocha -> "Stock desktop palette"
    AppTheme.Monokai -> "Editor palette"
}

@Composable
private fun ThemeSwatch(palette: OmasharePalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SwatchDot(palette.background)
        SwatchDot(palette.surface)
        SwatchDot(palette.accent)
        SwatchDot(palette.green)
    }
}

@Composable
private fun SwatchDot(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MochaOverlay, CircleShape),
    )
}
