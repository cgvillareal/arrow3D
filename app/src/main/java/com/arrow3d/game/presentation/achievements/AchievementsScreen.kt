package com.arrow3d.game.presentation.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arrow3d.domain.progress.Achievement
import com.arrow3d.domain.progress.PlayerStats
import com.arrow3d.game.R
import com.arrow3d.game.presentation.model.descriptionRes
import com.arrow3d.game.presentation.model.titleRes
import com.arrow3d.game.ui.theme.Arrow3dTheme
import com.arrow3d.game.ui.theme.GameColors
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat

@Composable
fun AchievementsScreenRoot(onBack: () -> Unit, viewModel: AchievementsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AchievementsScreen(state = state, onBack = onBack)
}

@Composable
fun AchievementsScreen(state: AchievementsState, onBack: () -> Unit) {
    val number = NumberFormat.getIntegerInstance()
    val s = state.stats
    val rows = listOf(
        R.string.stats_levels_won to number.format(s.levelsWon),
        R.string.stats_levels_lost to number.format(s.levelsLost),
        R.string.stats_perfect to number.format(s.perfectWins),
        R.string.stats_arrows to number.format(s.arrowsRemoved),
        R.string.stats_bent to number.format(s.bentRemoved),
        R.string.stats_travelers to number.format(s.travelersRemoved),
        R.string.stats_mistakes to number.format(s.mistakes),
        R.string.stats_best_combo to "×${s.bestCombo}",
        R.string.stats_best_score to number.format(s.bestLevelScore),
        R.string.stats_total_score to number.format(s.totalScore),
        R.string.stats_daily to number.format(s.dailyWins),
        R.string.stats_best_streak to number.format(s.bestDailyStreak),
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GameColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = GameColors.TextPrimary,
                    )
                }
                Column {
                    Text(stringResource(R.string.achievements_title), color = GameColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(
                        text = stringResource(R.string.achievements_progress, state.unlocked.size, state.all.size),
                        color = GameColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        items(state.all, key = { it.name }) { achievement ->
            AchievementRow(achievement, unlocked = achievement in state.unlocked)
        }
        item {
            Text(
                text = stringResource(R.string.stats_title),
                color = GameColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
        }
        items(rows, key = { it.first }) { (label, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                Text(stringResource(label), color = GameColors.TextSecondary, modifier = Modifier.weight(1f))
                Text(value, color = GameColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement, unlocked: Boolean) {
    val title = stringResource(achievement.titleRes())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GameColors.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (unlocked) GameColors.Star else GameColors.ProgressTrack),
        ) {
            Icon(
                imageVector = if (unlocked) Icons.Filled.Star else Icons.Filled.Lock,
                contentDescription = null,
                tint = if (unlocked) GameColors.Background else GameColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (unlocked) GameColors.TextPrimary else GameColors.TextSecondary,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(achievement.descriptionRes()), color = GameColors.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14172B, widthDp = 400, heightDp = 820)
@Composable
private fun AchievementsScreenPreview() {
    val stats = PlayerStats(levelsWon = 12, arrowsRemoved = 640, bestCombo = 6, bestLevelScore = 4_200, tutorialDone = true)
    Arrow3dTheme {
        AchievementsScreen(state = AchievementsState(stats, Achievement.unlocked(stats)), onBack = {})
    }
}
