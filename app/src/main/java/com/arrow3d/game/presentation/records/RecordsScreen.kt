package com.arrow3d.game.presentation.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arrow3d.domain.modes.Leaderboard
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.domain.modes.ScoreEntry
import com.arrow3d.game.R
import com.arrow3d.online.LeaderboardEntry
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
    RecordsScreen(state = state, onBack = onBack, onEvent = viewModel::onEvent)
}

@Composable
fun RecordsScreen(state: RecordsState, onBack: () -> Unit, onEvent: (RecordsEvent) -> Unit) {
    if (state.world.confirmingDelete) DeleteAccountDialog(onEvent)
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
                    Text(
                        text = stringResource(if (state.tab == RecordsTab.LOCAL) R.string.records_subtitle else R.string.records_world_subtitle),
                        color = GameColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GameColors.Card)
                    .padding(4.dp),
            ) {
                TabButton(stringResource(R.string.records_tab_local), state.tab == RecordsTab.LOCAL, Modifier.weight(1f)) {
                    onEvent(RecordsEvent.OnTabSelected(RecordsTab.LOCAL))
                }
                TabButton(stringResource(R.string.records_tab_world), state.tab == RecordsTab.WORLD, Modifier.weight(1f)) {
                    onEvent(RecordsEvent.OnTabSelected(RecordsTab.WORLD))
                }
            }
        }
        if (state.tab == RecordsTab.WORLD) {
            worldItems(state.world, onEvent)
            item { Spacer(Modifier.height(24.dp)) }
            return@LazyColumn
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

private fun LazyListScope.worldItems(world: WorldState, onEvent: (RecordsEvent) -> Unit) {
    if (world.account != null) {
        item(key = "delete_account") {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = { onEvent(RecordsEvent.OnDeleteRequested) }, enabled = !world.deleting) {
                    Text(stringResource(R.string.records_delete_account), color = GameColors.TextSecondary, fontSize = 13.sp)
                }
                if (world.deleteFailed) {
                    Text(stringResource(R.string.records_world_error), color = GameColors.ArrowBlocked, fontSize = 12.sp)
                }
            }
        }
    }
    when {
        !world.configured -> item { Message(stringResource(R.string.records_world_off)) }
        !world.accountLoaded -> item { Loading() }
        world.account == null -> item { JoinForm(world, onEvent) }
        else -> {
            item {
                Text(
                    text = stringResource(R.string.records_playing_as, world.account.name),
                    color = GameColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 10.dp),
                ) {
                    RecordsViewModel.DISPLAY_ORDER.forEach { mode ->
                        ModeChip(mode, selected = mode == world.mode) { onEvent(RecordsEvent.OnWorldModeSelected(mode)) }
                    }
                }
            }
            val board = world.board
            when {
                world.loading && board == null -> item { Loading() }
                world.failed && board == null -> item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Message(stringResource(R.string.records_world_error))
                        TextButton(onClick = { onEvent(RecordsEvent.OnRetry) }) {
                            Text(stringResource(R.string.records_world_retry), color = GameColors.ArrowRest)
                        }
                    }
                }
                board == null || board.entries.isEmpty() -> item { Message(stringResource(R.string.records_empty)) }
                else -> {
                    if (board.day != null) {
                        item { Text(stringResource(R.string.records_world_today), color = GameColors.TextSecondary, fontSize = 12.sp) }
                    }
                    itemsIndexed(board.entries, key = { _, e -> "world_${e.playerId}" }) { _, entry ->
                        WorldRow(entry, isMe = entry.playerId == world.account.id)
                    }
                    val me = board.me
                    if (me != null && board.entries.none { it.playerId == me.playerId }) {
                        item {
                            Text(
                                text = stringResource(R.string.records_world_me),
                                color = GameColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        item { WorldRow(me, isMe = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteAccountDialog(onEvent: (RecordsEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(RecordsEvent.OnDeleteCancelled) },
        title = { Text(stringResource(R.string.records_delete_title)) },
        text = { Text(stringResource(R.string.records_delete_body)) },
        confirmButton = {
            TextButton(onClick = { onEvent(RecordsEvent.OnDeleteConfirmed) }) {
                Text(stringResource(R.string.records_delete_confirm), color = GameColors.ArrowBlocked)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(RecordsEvent.OnDeleteCancelled) }) {
                Text(stringResource(R.string.records_delete_cancel))
            }
        },
        containerColor = GameColors.Card,
        titleContentColor = GameColors.TextPrimary,
        textContentColor = GameColors.TextSecondary,
    )
}

@Composable
private fun JoinForm(world: WorldState, onEvent: (RecordsEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GameColors.Card)
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.records_join_title), color = GameColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(stringResource(R.string.records_join_desc), color = GameColors.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = world.nameInput,
            onValueChange = { onEvent(RecordsEvent.OnNameChanged(it)) },
            placeholder = { Text(stringResource(R.string.records_join_hint)) },
            singleLine = true,
            isError = world.joinError == JoinError.INVALID_NAME || world.joinError == JoinError.OFFENSIVE_NAME,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(RecordsEvent.OnJoin) }),
            modifier = Modifier.fillMaxWidth(),
        )
        when (world.joinError) {
            JoinError.INVALID_NAME -> Text(stringResource(R.string.records_join_invalid), color = GameColors.ArrowBlocked, fontSize = 12.sp)
            JoinError.OFFENSIVE_NAME -> Text(stringResource(R.string.records_join_offensive), color = GameColors.ArrowBlocked, fontSize = 12.sp)
            JoinError.CONNECTION -> Text(stringResource(R.string.records_world_error), color = GameColors.ArrowBlocked, fontSize = 12.sp)
            null -> Unit
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onEvent(RecordsEvent.OnJoin) },
            enabled = !world.joining,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GameColors.ArrowRest, contentColor = GameColors.Background),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (world.joining) {
                CircularProgressIndicator(color = GameColors.Background, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Text(stringResource(R.string.records_join_button), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) GameColors.CardPressed else GameColors.Card)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected) GameColors.TextPrimary else GameColors.TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ModeChip(mode: PlayMode, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = stringResource(mode.nameRes()),
        color = if (selected) GameColors.Background else GameColors.TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) mode.accent() else GameColors.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun WorldRow(entry: LeaderboardEntry, isMe: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isMe) GameColors.CardPressed else GameColors.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = "${entry.rank}",
            color = if (entry.rank == 1) GameColors.Star else GameColors.TextSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, color = GameColors.TextPrimary, fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal)
            Text(stringResource(R.string.records_row_detail, entry.level, entry.bestCombo, LocalDate.ofEpochDay(entry.day).format(DATE_FORMAT)), color = GameColors.TextSecondary, fontSize = 12.sp)
        }
        Text(stringResource(R.string.records_row_score, entry.score), color = GameColors.TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Message(text: String) {
    Text(text = text, color = GameColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp))
}

@Composable
private fun Loading() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 32.dp)) {
        CircularProgressIndicator(color = GameColors.ArrowRest)
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
            onEvent = {},
        )
    }
}
