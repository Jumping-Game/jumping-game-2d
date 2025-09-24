@file:Suppress("FunctionName")

package com.bene.jump.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bene.jump.R
import com.bene.jump.data.Settings

@Composable
fun SettingsScreen(
    settings: Settings,
    onMusicChanged: (Boolean) -> Unit,
    onTiltChanged: (Float) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(text = stringResource(id = R.string.settings_title))
        RowItem(label = stringResource(id = R.string.settings_music)) {
            Switch(checked = settings.musicEnabled, onCheckedChange = onMusicChanged)
        }
        Text(text = stringResource(id = R.string.settings_tilt))
        Slider(
            value = settings.tiltSensitivity,
            onValueChange = onTiltChanged,
            valueRange = 0.5f..2f,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text(text = "Back")
        }
    }
}

@Composable
private fun RowItem(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        content()
    }
}
