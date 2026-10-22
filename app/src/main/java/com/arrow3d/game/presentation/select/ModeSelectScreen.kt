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
import androidx.compose.runtime.rememberUpdatedState
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
import com.arrow3d.domain.modes.AdaptiveDifficulty
import com.arrow3d.domain.modes.DailyChallenge
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.game.R
import com.arrow3d.game.presentation.model.accent
import com.arrow3d.game.presentation.model.descriptionRes
import com.arrow3d.game.presentation.model.nameRes
import com.arrow3d.game.ui.theme.Arrow3dTheme
import com.arrow3d.game.ui.theme.GameColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun ModeSelectScreenRoot(
    onOpenGame: (PlayMode) -> Unit,
    onOpenRecords: () -> Unit,
    viewModel: ModeSelectViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenGame by rememberUpdatedState(onOpenGame)
    val currentOnOpenRecords by rememberUpdatedState(onOpenRecords)
    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is ModeSelectSideEffect.OpenGame -> currentOnOpenGame(effect.mode)
                ModeSelectSideEffect.OpenRecords -> currentOnOpenRecords()
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
            val dailyDifficulty = DailyChallenge.difficultyFor(state.today)
            val dailyDesc = stringResource(
                R.string.select_daily_desc,
                dailyDifficulty.gridSize,
                stringResource(dailyDifficulty.nameRes()),
            )
            ModeCard(
                name = stringResource(R.string.mode_daily),
                description = if (state.dailyStreak > 0) {
                    stringResource(R.string.select_daily_streak, dailyDesc, state.dailyStreak)
                } else {
                    dailyDesc
                },
                trailing = stringResource(if (state.dailyDoneToday) R.string.select_daily_done else R.string.select_daily_play),
                accent = PlayMode.Daily.accent(),
                enabled = true,
                onClick = { onEvent(ModeSelectEvent.OnModeSelected(PlayMode.Daily)) },
            )
            Difficulty.entries.forEach { difficulty ->
                val mode = PlayMode.Classic(difficulty)
                ModeCard(
                    name = stringResource(difficulty.nameRes()),
                    description = withRecord(stringResource(difficulty.descriptionRes()), state.bestScores[mode.key]),
                    trailing = stringResource(R.string.select_level, state.levels[difficulty] ?: 1),
                    accent = difficulty.accent(),
                    enabled = true,
                    onClick = { onEvent(ModeSelectEvent.OnModeSelected(mode)) },
                )
            }
            val adaptivePreset = AdaptiveDifficulty.preset(state.adaptive.rating)
            ModeCard(
                name = stringResource(R.string.mode_adaptive),
                description = withRecord(
                    stringResource(R.string.select_adaptive_now, stringResource(adaptivePreset.nameRes())),
                    state.bestScores[PlayMode.Adaptive.key],
                ),
                trailing = stringResource(R.string.select_level, state.adaptive.level),
                accent = PlayMode.Adaptive.accent(),
                enabled = true,
                onClick = { onEvent(ModeSelectEvent.OnModeSelected(PlayMode.Adaptive)) },
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(GameColors.Card)
                .clickable { onEvent(ModeSelectEvent.OnRecordsSelected) }
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.select_records),
                color = GameColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(text = "›", color = GameColors.TextSecondary, fontSize = 22.sp)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.select_settings),
            color = GameColors.TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(GameColors.Card)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            SettingRow(
                label = stringResource(R.string.select_vibration),
                checked = state.settings.vibration,
                onChange = { onEvent(ModeSelectEvent.OnVibrationToggled(it)) },
            )
            SettingRow(
                label = stringResource(R.string.select_sound),
                checked = state.settings.sound,
                onChange = { onEvent(ModeSelectEvent.OnSoundToggled(it)) },
            )
            SettingRow(
                label = stringResource(R.string.select_color_blind),
                description = stringResource(R.string.select_color_blind_desc),
                checked = state.settings.colorBlind,
                onChange = { onEvent(ModeSelectEvent.OnColorBlindToggled(it)) },
            )
        }
    }
}

@Composable
private fun withRecord(description: String, best: Int?): String =
    if (best == null) description else stringResource(R.string.select_with_record, description, best)

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = GameColors.TextPrimary)
            if (description != null) Text(text = description, color = GameColors.TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GameColors.Background,
                checkedTrackColor = GameColors.ArrowRest,
            ),
        )
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
            state = ModeSelectState(
                levels = Difficulty.entries.associateWith { it.ordinal * 7 + 1 },
                today = 20_004,
                bestScores = mapOf("easy" to 1_250, "adaptive" to 3_400),
            ),
            onEvent = {},
        )
    }
}
