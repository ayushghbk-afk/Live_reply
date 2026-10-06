package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.LiveAiReplyApplication
import com.example.ui.screens.AiSettingsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.PersonaManagerScreen
import com.example.ui.screens.PrivacySettingsScreen
import com.example.ui.screens.SetupWizardScreen
import com.example.ui.screens.SupportedAppsScreen
import com.example.ui.screens.TestModeScreen

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_WIZARD = "wizard"
const val ROUTE_AI_SETTINGS = "ai_settings"
const val ROUTE_PERSONAS = "personas"
const val ROUTE_SUPPORTED_APPS = "supported_apps"
const val ROUTE_PRIVACY = "privacy"
const val ROUTE_TEST_MODE = "test_mode"
const val ROUTE_LOGS = "logs"

@Composable
fun AppNavigation(
    application: LiveAiReplyApplication,
    onRequestScreenCapture: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    val repository = application.repository
    val secureStorage = application.secureStorage
    val aiProvider = application.aiProvider

    val startDestination = if (secureStorage.isFirstRunCompleted) ROUTE_DASHBOARD else ROUTE_WIZARD

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(
                repository = repository,
                onRequestScreenCapture = onRequestScreenCapture,
                onNavigateToAiSettings = { navController.navigate(ROUTE_AI_SETTINGS) },
                onNavigateToPersonas = { navController.navigate(ROUTE_PERSONAS) },
                onNavigateToSupportedApps = { navController.navigate(ROUTE_SUPPORTED_APPS) },
                onNavigateToPrivacy = { navController.navigate(ROUTE_PRIVACY) },
                onNavigateToTestMode = { navController.navigate(ROUTE_TEST_MODE) },
                onNavigateToLogs = { navController.navigate(ROUTE_LOGS) },
                onOpenWizard = { navController.navigate(ROUTE_WIZARD) }
            )
        }

        composable(ROUTE_WIZARD) {
            SetupWizardScreen(
                repository = repository,
                aiProvider = aiProvider,
                onFinishWizard = {
                    navController.navigate(ROUTE_DASHBOARD) {
                        popUpTo(ROUTE_WIZARD) { inclusive = true }
                    }
                }
            )
        }

        composable(ROUTE_AI_SETTINGS) {
            AiSettingsScreen(
                secureStorage = secureStorage,
                repository = repository,
                aiProvider = aiProvider,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_PERSONAS) {
            PersonaManagerScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_SUPPORTED_APPS) {
            SupportedAppsScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_PRIVACY) {
            PrivacySettingsScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_TEST_MODE) {
            TestModeScreen(
                repository = repository,
                aiProvider = aiProvider,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_LOGS) {
            LogsScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
