package com.arrow3d.game.presentation.records

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arrow3d.domain.modes.Leaderboard
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.domain.modes.ScoreEntry
import com.arrow3d.game.R
import com.arrow3d.game.presentation.model.accent
import com.arrow3d.game.presentation.model.nameRes
import com.arrow3d.game.ui.theme.Arrow3dTheme
import com.arrow3d.game.ui.theme.GameColors
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun RecordsScreenRoot(onBack: () -> Unit, viewModel: RecordsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecordsScreen(state = state, onBack = onBack)
}

@Composable
fun RecordsScreen(state: RecordsState, onBack: () -> Unit) {
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
                    Text(stringResource(R.string.records_title), color = GameColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(stringResource(R.string.records_subtitle), color = GameColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }
        state.boards.forEach { (mode, board) ->
            item(key = mode.key) {
                ModeHeader(mode)
            }
            if (board.entries.isEmpty()) {
                item(key = "${mode.key}_empty") {
                    Text(
                        text = stringResource(R.string.records_empty),
                        color = GameColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                }
            } else {
                itemsIndexed(board.entries, key = { i, _ -> "${mode.key}_$i" }) { i, entry ->
                    RecordRow(rank = i + 1, entry = entry, daily = mode == PlayMode.Daily)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ModeHeader(mode: PlayMode) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(mode.accent()),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(mode.nameRes()), color = GameColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun RecordRow(rank: Int, entry: ScoreEntry, daily: Boolean) {
    val date = LocalDate.ofEpochDay(entry.day).format(DATE_FORMAT)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GameColors.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = "$rank",
            color = if (rank == 1) GameColors.Star else GameColors.TextSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.records_row_score, entry.score), color = GameColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                text = if (daily) {
                    stringResource(R.string.records_daily_detail, entry.bestCombo, date)
                } else {
                    stringResource(R.string.records_row_detail, entry.level, entry.bestCombo, date)
                },
                color = GameColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Preview(showBackground = true, backgroundColor = 0xFF14172B, widthDp = 400, heightDp = 820)
@Composable
private fun RecordsScreenPreview() {
    val board = Leaderboard(listOf(ScoreEntry(2_350, 6, 14, 20_004), ScoreEntry(1_200, 3, 9, 20_001)))
    Arrow3dTheme {
        RecordsScreen(
            state = RecordsState(listOf(PlayMode.Daily to Leaderboard(), PlayMode.Adaptive to board)),
            onBack = {},
        )
    }
}
