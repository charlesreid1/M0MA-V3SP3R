package com.vesper.flipper.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vesper.flipper.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LabsHubViewModel @Inject constructor(
    settingsStore: SettingsStore,
) : ViewModel() {
    /** Whether the Campaigns entry is enabled (Ralph mode has to be turned on in Settings). */
    val ralphEnabled = settingsStore.ralphEnabled
}
