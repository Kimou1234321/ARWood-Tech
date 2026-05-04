package com.algerianwood.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.algerianwood.app.data.SessionData
import com.algerianwood.app.ui.calibration.CalibrationScreen
import com.algerianwood.app.ui.camera.CameraScreen
import com.algerianwood.app.ui.home.HomeScreen
import com.algerianwood.app.ui.login.LoginScreen
import com.algerianwood.app.ui.results.ResultsScreen

/**
 * Central navigation graph for the app.
 *
 * Flow: Login → Home → (Camera | Gallery) → Calibration → Results
 *
 * Image and calibration data are passed via [SessionData] singleton
 * rather than navigation arguments to avoid URI encoding issues.
 */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CAMERA = "camera"
    const val CALIBRATION = "calibration"
    const val RESULTS = "results"
}

@Composable
fun AppNavGraph() {
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
                }
            )
        }

        // ── Home ──
        composable(Routes.HOME) {
            HomeScreen(
                onImageReady = {
                    navController.navigate(Routes.CALIBRATION)
                },
                onTakePicture = {
                    navController.navigate(Routes.CAMERA)
                }
            )
        }

        // ── Camera ──
        composable(Routes.CAMERA) {
            CameraScreen(
                onImageCaptured = {
                    navController.navigate(Routes.CALIBRATION) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
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
    }
}
