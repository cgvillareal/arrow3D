package com.arrow3d.game.presentation.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arrow3d.domain.engine.ComboRules
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.game.audio.IMusicPlayer
import com.arrow3d.game.presentation.game.cube.ArrowFxState
import com.arrow3d.game.presentation.game.cube.rememberCubeCameraState
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun GameScreenRoot(
    mode: PlayMode,
    onBack: () -> Unit,
    viewModel: GameViewModel = koinViewModel(key = mode.key) { parametersOf(mode) },
    music: IMusicPlayer = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Music plays while this screen is visible; it stops when the app goes to the background.
    LifecycleStartEffect(state.musicEnabled) {
        if (state.musicEnabled) music.start() else music.stop()
        onStopOrDispose { music.stop() }
    }
    // The combo swells the music; once its window runs out, it calms down again.
    LaunchedEffect(state.comboStartedAt, state.combo) {
        music.setIntensity(((state.combo - 1) / (ComboRules.MAX_MULTIPLIER - 1f)).coerceIn(0f, 1f))
        delay(ComboRules.WINDOW_MS)
        music.setIntensity(0f)
    }
    val camera = rememberCubeCameraState()
    val fx = remember { ArrowFxState() }
    val currentState by rememberUpdatedState(state)
    val currentOnBack by rememberUpdatedState(onBack)

    // New level or restart: drop running animations and recentre.
    LaunchedEffect(state.session) {
        fx.clear()
        camera.reset()
    }

    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is GameSideEffect.Fx -> {
                    currentState.puzzle?.level?.let { level -> fx.onFx(effect.fx, level) }
                    val fxEvent = effect.fx
                    if (fxEvent is ArrowFx.Released && fxEvent.levelComplete) camera.celebrate()
                }
                GameSideEffect.NavigateBack -> currentOnBack()
            }
        }
    }

    GameScreen(
        state = state,
        camera = camera,
        fx = fx,
        onEvent = viewModel::onEvent,
    )
}
