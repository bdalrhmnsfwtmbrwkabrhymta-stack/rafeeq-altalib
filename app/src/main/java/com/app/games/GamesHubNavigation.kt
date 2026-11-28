package com.app.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * نقطة الدخول المستقلة لقسم «مركز الألعاب التعليمية» ولعبة «ركض المتفوقين» (com.app.games.*).
 */
@Composable
fun GamesHubNavigation(
    onNavigateBackToApp: () -> Unit,
    gamesViewModel: GamesHubViewModel = viewModel()
) {
    val shopState by gamesViewModel.shopState.collectAsStateWithLifecycle()
    val uiState by gamesViewModel.uiState.collectAsStateWithLifecycle()

    val activeStage = uiState.activeRunStageNumber
    if (activeStage != null) {
        StudyRunnerGameScreen(
            stageNumber = activeStage,
            questions = uiState.activeRunQuestions,
            shopState = shopState,
            onQuestionShown = gamesViewModel::markQuestionAsked,
            onRunFinished = { stage, victory, trackCoins, score, correct, wrong, maxCombo ->
                gamesViewModel.finishRunAndPersist(
                    stageNumber = stage,
                    victory = victory,
                    trackCoins = trackCoins,
                    score = score,
                    correctAnswers = correct,
                    wrongAnswers = wrong,
                    maxCombo = maxCombo
                )
            },
            onAdvanceToStage = { nextStage ->
                gamesViewModel.startStage(nextStage)
            },
            onRestartStage = { sameStage ->
                gamesViewModel.startStage(sameStage)
            },
            onExitToHub = {
                gamesViewModel.exitActiveRun()
            }
        )
    } else {
        GamesCenterScreen(
            viewModel = gamesViewModel,
            onNavigateBackToApp = onNavigateBackToApp
        )
    }
}
