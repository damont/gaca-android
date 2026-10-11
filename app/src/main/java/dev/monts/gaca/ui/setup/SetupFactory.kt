package dev.monts.gaca.ui.setup

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.monts.gaca.BuildConfig
import dev.monts.gaca.GacaApp
import dev.monts.gaca.data.repo.SetupRepository
import dev.monts.gaca.di.AppContainer

/** The pairing screen's view-model; a successful pairing rebuilds the app around the new server. */
@Composable
fun rememberSetupViewModel(app: GacaApp, container: AppContainer, initialAddress: String = ""): SetupViewModel =
    viewModel(
        key = "setup",
        factory = remember(container) {
            viewModelFactory {
                initializer {
                    SetupViewModel(
                        setup = SetupRepository(),
                        settingsStore = container.settingsStore,
                        tokenStore = container.tokenStore,
                        allowHttp = BuildConfig.DEBUG,
                        deviceName = Build.MODEL ?: "Phone",
                        initialAddress = initialAddress,
                        onPaired = app::reconfigure,
                    )
                }
            }
        },
    )
