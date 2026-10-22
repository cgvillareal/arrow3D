package com.arrow3d.game.presentation.select

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.game.R
import com.arrow3d.game.presentation.model.accent
import com.arrow3d.game.presentation.model.descriptionRes
import com.arrow3d.game.presentation.model.nameRes
import com.arrow3d.game.ui.theme.Arrow3dTheme
import com.arrow3d.game.ui.theme.GameColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun ModeSelectScreenRoot(
    onOpenGame: (Difficulty) -> Unit,
    viewModel: ModeSelectViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is ModeSelectSideEffect.OpenGame -> onOpenGame(effect.difficulty)
            }
        }
    }
    ModeSelectScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun ModeSelectScreen(state: ModeSelectState, onEvent: (ModeSelectEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GameColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            color = GameColors.ArrowRest,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.select_title),
            color = GameColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
        )
        Text(
            text = stringResource(R.string.select_subtitle),
            color = GameColors.TextSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Difficulty.entries.forEach { difficulty ->
                ModeCard(
                    name = stringResource(difficulty.nameRes()),
                    description = stringResource(difficulty.descriptionRes()),
                    trailing = stringResource(R.string.select_level, state.levels[difficulty] ?: 1),
                    accent = difficulty.accent(),
                    enabled = true,
                    onClick = { onEvent(ModeSelectEvent.OnModeSelected(difficulty)) },
                )
            }
            // Adaptive mode is planned for the content phase.
            ModeCard(
                name = stringResource(R.string.mode_adaptive),
                description = stringResource(R.string.mode_adaptive_desc),
                trailing = stringResource(R.string.select_coming_soon),
                accent = GameColors.Adaptive,
                enabled = false,
                onClick = {},
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(GameColors.Card)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.select_vibration),
                color = GameColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.vibrationEnabled,
                onCheckedChange = { onEvent(ModeSelectEvent.OnVibrationToggled(it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = GameColors.Background,
                    checkedTrackColor = GameColors.ArrowRest,
                ),
            )
        }
    }
}

@Composable
private fun ModeCard(
    name: String,
    description: String,
    trailing: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GameColors.Card)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(if (enabled) accent else accent.copy(alpha = 0.4f)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = name,
                color = if (enabled) GameColors.TextPrimary else GameColors.TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Text(text = description, color = GameColors.TextSecondary, fontSize = 13.sp)
        }
        Text(
            text = trailing,
            color = GameColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14172B, widthDp = 400, heightDp = 820)
@Composable
private fun ModeSelectScreenPreview() {
    Arrow3dTheme {
        ModeSelectScreen(
            state = ModeSelectState(levels = Difficulty.entries.associateWith { it.ordinal * 7 + 1 }),
            onEvent = {},
        )
    }
}
