package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.model.Role
import com.example.ui.screens.counter.CounterScreen
import com.example.ui.screens.login.PinLoginScreen
import com.example.ui.screens.manager.ManagerScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.waiter.WaiterScreen
import com.example.ui.screens.wizard.SetupWizardScreen
import com.example.ui.theme.CaféFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = CaféFlowApp.instance.repository

        setContent {
            CaféFlowTheme {
                val navController = rememberNavController()
                val currentStaff by repository.currentStaff.collectAsState()
                val currentRole by repository.currentRole.collectAsState()

                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "login" // Starts with login screen in all cases
                    ) {
                        composable("login") {
                            PinLoginScreen(
                                repository = repository,
                                onRoleSelected = { role, staff ->
                                    when (role) {
                                        Role.COUNTER -> navController.navigate("counter") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                        Role.WAITER -> navController.navigate("waiter") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                        Role.MANAGER -> navController.navigate("manager") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                },
                                onOpenWizard = {
                                    navController.navigate("wizard")
                                }
                            )
                        }

                        composable("counter") {
                            currentStaff?.let { staff ->
                                CounterScreen(
                                    repository = repository,
                                    currentStaff = staff,
                                    onNavigateToRoleSelect = {
                                        repository.logout()
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
                                    }
                                )
                            } ?: run {
                                LaunchedEffect(Unit) {
                                    navController.navigate("login") {
                                        popUpTo("counter") { inclusive = true }
                                    }
                                }
                            }
                        }

                        composable("waiter") {
                            currentStaff?.let { staff ->
                                WaiterScreen(
                                    repository = repository,
                                    currentStaff = staff,
                                    onNavigateToRoleSelect = {
                                        repository.logout()
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                )
                            } ?: run {
                                LaunchedEffect(Unit) {
                                    navController.navigate("login") {
                                        popUpTo("waiter") { inclusive = true }
                                    }
                                }
                            }
                        }

                        composable("manager") {
                            currentStaff?.let { staff ->
                                ManagerScreen(
                                    repository = repository,
                                    currentStaff = staff,
                                    onNavigateToRoleSelect = {
                                        repository.logout()
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    },
                                    onNavigateToWizard = {
                                        navController.navigate("wizard")
                                    }
                                )
                            } ?: run {
                                LaunchedEffect(Unit) {
                                    navController.navigate("login") {
                                        popUpTo("manager") { inclusive = true }
                                    }
                                }
                            }
                        }

                        composable("wizard") {
                            SetupWizardScreen(
                                repository = repository,
                                onWizardCompleted = {
                                    repository.logout()
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                repository = repository,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onRerunWizard = {
                                    navController.navigate("wizard")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
