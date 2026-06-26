package com.algerianwood.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.algerianwood.app.data.SessionData
import com.algerianwood.app.ui.calibration.CalibrationScreen
import com.algerianwood.app.ui.camera.CameraScreen
import com.algerianwood.app.ui.crop.CropScreen
import com.algerianwood.app.ui.home.HomeScreen
import com.algerianwood.app.ui.login.LoginScreen
import com.algerianwood.app.ui.login.RegisterScreen
import com.algerianwood.app.ui.results.ResultsScreen
import com.algerianwood.app.ui.results.SavedAnalysesScreen
import com.algerianwood.app.ui.results.SavedAnalysisDetailScreen
import com.algerianwood.app.ui.theme.AppThemeMode

/**
 * Central navigation graph for the app.
 *
 * Flow: Login → Home → (Camera | Gallery) → Crop → Calibration → Results
 *
 * Image and calibration data are passed via [SessionData] singleton
 * rather than navigation arguments to avoid URI encoding issues.
 */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val REGISTER = "register"
    const val CAMERA = "camera"
    const val CROP = "crop"
    const val CALIBRATION = "calibration"
    const val RESULTS = "results"
    const val SAVED_ANALYSES = "saved_analyses"
    const val SAVED_ANALYSIS_DETAIL = "saved_analysis_detail"
}

@Composable
fun AppNavGraph(
    themeMode: AppThemeMode = AppThemeMode.NIGHT,
    onToggleTheme: () -> Unit = {}
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN
    ) {
        // ── Login ──
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onCreateAccount = { navController.navigate(Routes.REGISTER) }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        // ── Home ──
        composable(Routes.HOME) {
            HomeScreen(
                themeMode = themeMode,
                onToggleTheme = onToggleTheme,
                onImageReady = {
                    navController.navigate(Routes.CROP)
                },
                onTakePicture = {
                    navController.navigate(Routes.CAMERA)
                },
                onOpenSavedAnalyses = {
                    navController.navigate(Routes.SAVED_ANALYSES)
                }
            )
        }

        // ── Camera ──
        composable(Routes.CAMERA) {
            CameraScreen(
                onImageCaptured = {
                    navController.navigate(Routes.CROP) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.CROP) {
            CropScreen(
                onConfirm = {
                    navController.navigate(Routes.CALIBRATION)
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Calibration ──
        composable(Routes.CALIBRATION) {
            CalibrationScreen(
                onAnalyze = {
                    navController.navigate(Routes.RESULTS)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // ── Results ──
        composable(Routes.RESULTS) {
            ResultsScreen(
                onNewAnalysis = {
                    SessionData.clear()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SAVED_ANALYSES) {
            SavedAnalysesScreen(
                onBack = { navController.popBackStack() },
                onOpenSaved = { id ->
                    SessionData.selectedSavedAnalysisId = id
                    navController.navigate(Routes.SAVED_ANALYSIS_DETAIL)
                }
            )
        }

        composable(Routes.SAVED_ANALYSIS_DETAIL) {
            SavedAnalysisDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
