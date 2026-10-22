package com.arrow3d.game.presentation.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.game.presentation.game.cube.ArrowFxState
import com.arrow3d.game.presentation.game.cube.rememberCubeCameraState
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GameScreenRoot(
    difficulty: Difficulty,
    onBack: () -> Unit,
    viewModel: GameViewModel = koinViewModel(key = difficulty.name) { parametersOf(difficulty) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
