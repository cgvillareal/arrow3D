package com.arrow3d.game.presentation.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arrow3d.domain.engine.ComboRules
import com.arrow3d.domain.engine.PuzzleEngine
import com.arrow3d.domain.engine.PuzzleStatus
import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.generator.GeneratorConfig
import com.arrow3d.domain.generator.LevelGenerator
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.game.R
import com.arrow3d.game.presentation.game.cube.ArrowFxState
import com.arrow3d.game.presentation.game.cube.CubeCameraState
import com.arrow3d.game.presentation.game.cube.CubeView
import com.arrow3d.game.presentation.game.cube.rememberCubeCameraState
import com.arrow3d.game.presentation.model.nameRes
import com.arrow3d.game.ui.theme.Arrow3dTheme
import com.arrow3d.game.ui.theme.ArrowPalette
import com.arrow3d.game.ui.theme.GameColors
import kotlinx.coroutines.launch

@Composable
fun GameScreen(
    state: GameState,
    camera: CubeCameraState,
    fx: ArrowFxState,
    onEvent: (GameEvent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GameColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GameHud(state = state, onBack = { onEvent(GameEvent.OnBack) })

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val puzzle = state.puzzle
                if (state.isLoading || puzzle == null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = GameColors.ArrowRest)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.game_generating), color = GameColors.TextSecondary)
                    }
                } else {
                    CubeView(
                        level = puzzle.level,
                        remaining = puzzle.remaining,
                        hintArrowId = state.hintArrowId,
                        camera = camera,
                        fx = fx,
                        enabled = state.status == PuzzleStatus.PLAYING,
                        onArrowTap = { hit -> onEvent(GameEvent.OnArrowTapped(hit.arrowId, hit.ambiguous)) },
                        modifier = Modifier.fillMaxSize(),
                        palette = ArrowPalette.of(state.colorBlind),
                    )
                    ComboBadge(
                        combo = state.combo,
                        points = state.lastPoints,
                        startedAt = state.comboStartedAt,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.game_rotate_hint),
                        color = GameColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                    )
                }
            }

            GameActions(state = state, onEvent = onEvent)
        }

        AnimatedVisibility(
            visible = state.status == PuzzleStatus.WON,
            enter = fadeIn(tween(350, delayMillis = 950)) + slideInVertically(tween(350, delayMillis = 950)) { it / 4 },
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val lives = if (state.livesLost == 0) {
                stringResource(R.string.result_no_lives_lost)
            } else {
                stringResource(R.string.result_lives_lost, state.livesLost)
            }
            val record = when (val rank = state.recordRank) {
                null -> null
                1 -> stringResource(R.string.result_new_record)
                else -> stringResource(R.string.result_rank, rank)
            }
            ResultCard(
                title = stringResource(if (state.isDaily) R.string.result_daily_complete else R.string.result_complete),
                subtitle = if (state.isDaily && state.dailyStreak > 0) {
                    stringResource(R.string.result_daily_streak, state.dailyStreak, lives)
                } else {
                    lives
                },
                stars = state.stars,
                detail = listOfNotNull(
                    stringResource(R.string.result_score, state.score, state.bestCombo),
                    record,
                    onlineLine(state.online),
                ).joinToString("\n"),
                // The daily challenge has no next level: it is one puzzle a day.
                primaryLabel = stringResource(if (state.isDaily) R.string.result_back else R.string.result_next),
                onPrimary = { onEvent(if (state.isDaily) GameEvent.OnBack else GameEvent.OnNextLevel) },
                secondaryLabel = stringResource(R.string.result_replay),
                onSecondary = { onEvent(GameEvent.OnRestart) },
            )
        }

        AnimatedVisibility(
            visible = state.status == PuzzleStatus.LOST,
            enter = fadeIn(tween(300, delayMillis = 500)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ResultCard(
                title = stringResource(R.string.result_lost),
                subtitle = stringResource(R.string.result_lost_desc),
                stars = null,
                primaryLabel = stringResource(R.string.result_retry),
                onPrimary = { onEvent(GameEvent.OnRestart) },
                secondaryLabel = stringResource(R.string.result_back),
                onSecondary = { onEvent(GameEvent.OnBack) },
            )
        }
    }
}

@Composable
private fun GameHud(state: GameState, onBack: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = GameColors.TextPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.isDaily) stringResource(R.string.mode_daily) else stringResource(R.string.game_level, state.levelNumber),
                    color = GameColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text(
                    text = stringResource(
                        R.string.game_subtitle,
                        state.gridSize,
                        stringResource(state.mode.nameRes()),
                    ),
                    color = GameColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
            Lives(lives = state.lives, maxLives = state.maxLives)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { state.progress },
            color = GameColors.Progress,
            trackColor = GameColors.ProgressTrack,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.game_score, state.score),
                color = GameColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.game_progress, state.removed, state.total),
                color = GameColors.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * Shows the running combo from x2 on. The bar under it drains over the combo window: releasing
 * another arrow before it empties keeps the combo going. A blocked tap or an undo hides it.
 */
@Composable
private fun ComboBadge(combo: Int, points: Int, startedAt: Long?, modifier: Modifier = Modifier) {
    val timeLeft = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    // Keep the last values while the badge fades out after the combo ends.
    var shownCombo by remember { mutableIntStateOf(0) }
    var shownPoints by remember { mutableIntStateOf(0) }

    LaunchedEffect(startedAt, combo) {
        if (startedAt == null || combo < 2) {
            timeLeft.snapTo(0f)
            return@LaunchedEffect
        }
        shownCombo = combo
        shownPoints = points
        launch {
            pop.snapTo(1.35f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
        timeLeft.snapTo(1f)
        timeLeft.animateTo(0f, tween(ComboRules.WINDOW_MS.toInt(), easing = LinearEasing))
    }

    AnimatedVisibility(
        visible = combo >= 2 && timeLeft.value > 0f,
        enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.7f),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(RoundedCornerShape(16.dp))
                .background(GameColors.Card.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.game_combo, shownCombo),
                    color = GameColors.Star,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.game_combo_points, shownPoints),
                    color = GameColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { timeLeft.value },
                color = GameColors.Star,
                trackColor = GameColors.ProgressTrack,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .width(120.dp)
                    .height(4.dp),
            )
        }
    }
}

@Composable
private fun Lives(lives: Int?, maxLives: Int?) {
    if (lives == null || maxLives == null) {
        Text(stringResource(R.string.game_unlimited_lives), color = GameColors.TextSecondary, fontSize = 13.sp)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(maxLives) { index ->
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = if (index < lives) stringResource(R.string.cd_life) else null,
                tint = if (index < lives) GameColors.Heart else GameColors.ProgressTrack,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun GameActions(state: GameState, onEvent: (GameEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val playing = state.status == PuzzleStatus.PLAYING && !state.isLoading
        ActionButton(
            label = withCount(stringResource(R.string.game_undo), state.undosLeft),
            enabled = playing && state.canUndo,
            onClick = { onEvent(GameEvent.OnUndo) },
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            label = withCount(stringResource(R.string.game_hint), state.hintsLeft),
            enabled = playing && state.canHint && state.hintArrowId == null,
            onClick = { onEvent(GameEvent.OnHint) },
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            label = stringResource(R.string.game_restart),
            enabled = playing,
            onClick = { onEvent(GameEvent.OnRestart) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun onlineLine(online: OnlineResult): String? = when (online) {
    OnlineResult.None -> null
    OnlineResult.Sending -> stringResource(R.string.result_online_sending)
    OnlineResult.Queued -> stringResource(R.string.result_online_queued)
    is OnlineResult.Ranked -> stringResource(R.string.result_online_rank, online.rank)
}

@Composable
private fun withCount(label: String, count: Int?): String =
    if (count == null) label else stringResource(R.string.game_count, label, count)

@Composable
private fun ActionButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GameColors.Card,
            contentColor = GameColors.TextPrimary,
            disabledContainerColor = GameColors.Card.copy(alpha = 0.5f),
            disabledContentColor = GameColors.TextSecondary.copy(alpha = 0.5f),
        ),
        modifier = modifier.height(48.dp),
    ) {
        Text(label, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun ResultCard(
    title: String,
    subtitle: String,
    stars: Int?,
    detail: String? = null,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(GameColors.Card)
            .padding(24.dp),
    ) {
        Text(title, color = GameColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = GameColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        if (detail != null) {
            Spacer(Modifier.height(4.dp))
            Text(detail, color = GameColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        if (stars != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = if (i < stars) stringResource(R.string.cd_star) else null,
                        tint = if (i < stars) GameColors.Star else GameColors.ProgressTrack,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onPrimary,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GameColors.ArrowRest, contentColor = GameColors.Background),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            Text(primaryLabel, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
            Text(secondaryLabel, color = GameColors.TextPrimary)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14172B, widthDp = 400, heightDp = 820)
@Composable
private fun GameScreenPreview() {
    val difficulty = Difficulty.MEDIUM
    val level = LevelGenerator(GeneratorConfig.from(difficulty)).generate(LevelGenerator.seedFor(difficulty, 24)).level
    val puzzle = PuzzleEngine.start(level, difficulty.lives, difficulty.undos, difficulty.hints)
    Arrow3dTheme {
        GameScreen(
            state = GameState(
                mode = PlayMode.Classic(difficulty),
                levelNumber = 24,
                gridSize = difficulty.gridSize,
                isLoading = false,
                puzzle = puzzle,
            ),
            camera = rememberCubeCameraState(),
            fx = ArrowFxState(),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14172B, widthDp = 400, heightDp = 820)
@Composable
private fun GameScreenLoadingPreview() {
    Arrow3dTheme {
        GameScreen(
            state = GameState(mode = PlayMode.Classic(Difficulty.EASY)),
            camera = rememberCubeCameraState(),
            fx = ArrowFxState(),
            onEvent = {},
        )
    }
}
