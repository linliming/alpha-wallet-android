package com.alphawallet.app.viewmodel

import android.os.Environment
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.service.TransactionsService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AdvancedSettingsViewModel @Inject internal constructor(
    private val preferenceRepository: PreferenceRepositoryType,
    private val transactionsService: TransactionsService
) : BaseViewModel() {
    fun createDirectory(): Boolean {
        //create XML repository directory
        val directory = File(
            (Environment.getExternalStorageDirectory()
                .toString() + File.separator + HomeViewModel.ALPHAWALLET_DIR)
        )

        if (!directory.exists()) {
            return directory.mkdir()
        } else {
            return directory.exists()
        }
    }

    fun toggle1559Transactions(toggleState: Boolean) {
        preferenceRepository.use1559Transactions = toggleState
    }

    val transactions1559State : Boolean
        get() = preferenceRepository.use1559Transactions

    val developerOverrideState: Boolean
        get() = preferenceRepository.developerOverride

    fun toggleDeveloperOverride(toggleState: Boolean) {
        preferenceRepository.developerOverride = toggleState
    }

    var fullScreenState: Boolean
        get() = preferenceRepository.fullScreenState
        set(state) {
            preferenceRepository.fullScreenState = state
        }

    fun blankFilterSettings() {
        preferenceRepository.blankHasSetNetworkFilters()
    }

    suspend fun resetTokenData(): Boolean{
        return transactionsService.wipeDataForWalletSuspend()
    }

    fun stopChainActivity() {
        transactionsService.stopActivity()
    }

    fun toggleUseViewer(state: Boolean) {
        preferenceRepository.useTSViewer = state
    }

    val tokenScriptViewerState: Boolean
        get() = preferenceRepository.useTSViewer
}
