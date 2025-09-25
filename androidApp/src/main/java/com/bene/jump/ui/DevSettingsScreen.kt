@file:Suppress("FunctionName")

package com.bene.jump.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bene.jump.data.Settings

@Composable
fun DevSettingsScreen(
    settings: Settings,
    onMultiplayerEnabled: (Boolean) -> Unit,
    onWsUrlChanged: (String) -> Unit,
    onInputBatchChanged: (Boolean) -> Unit,
    onInterpolationDelayChanged: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Developer Settings")
        RowItem(label = "Multiplayer Enabled") {
            Switch(checked = settings.multiplayerEnabled, onCheckedChange = onMultiplayerEnabled)
        }
        Text(text = "Realtime WS URL")
        val urlState = remember(settings.wsUrl) { mutableStateOf(TextFieldValue(settings.wsUrl)) }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = urlState.value,
            onValueChange = {
                urlState.value = it
                onWsUrlChanged(it.text)
            },
            singleLine = true,
            label = { Text("wss://...") },
        )
        RowItem(label = "Send inputs as batch") {
            Switch(checked = settings.inputBatchEnabled, onCheckedChange = onInputBatchChanged)
        }
        Text(text = "Interpolation Delay (${settings.interpolationDelayMs} ms)")
        Slider(
            modifier = Modifier.fillMaxWidth(),
            value = settings.interpolationDelayMs.toFloat(),
            onValueChange = { value -> onInterpolationDelayChanged(value.toInt()) },
            valueRange = 0f..300f,
            steps = 5,
        )
        Button(onClick = onBack) { Text(text = "Back") }
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
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        content()
    }
}
