@file:Suppress("FunctionName")

package com.bene.jump.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bene.jump.R

@Composable
fun MenuScreen(
    canResume: Boolean,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(id = R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Button(onClick = onPlay) {
            Text(text = stringResource(id = R.string.menu_play))
        }
        if (canResume) {
            Button(onClick = onResume) {
                Text(text = stringResource(id = R.string.menu_resume))
            }
        }
        Button(onClick = onSettings) {
            Text(text = stringResource(id = R.string.menu_settings))
        }
    }
}
