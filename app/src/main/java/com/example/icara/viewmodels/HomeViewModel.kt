package com.example.icara.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.icara.ui.components.DialogOption
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class HomeViewModel: ViewModel() {
    private val _navigateToTalkScreen = Channel<String>()
    val navigateToTalkScreen = _navigateToTalkScreen.receiveAsFlow()
    private val _showLangOptDialog = MutableStateFlow(false)
    val showLangOptDialog: StateFlow<Boolean> = _showLangOptDialog.asStateFlow()

    private val _showSysDevDialog = MutableStateFlow(false)
    val showSysDevDialog: StateFlow<Boolean> = _showSysDevDialog.asStateFlow()

    fun onClickLangOptDialog() {
        _showLangOptDialog.value = true
    }

    fun onDismissLangOptDialog() {
        _showLangOptDialog.value = false
    }

    fun onClickLangOpt(opt: DialogOption) {
        _showLangOptDialog.value = false

        when (opt) {
            is DialogOption.Enabled -> {
                 viewModelScope.launch {
                    _navigateToTalkScreen.send(opt.lang)
                 }
            }
            is DialogOption.Disabled -> {
                _showSysDevDialog.value = true
            }
        }
    }

    fun onDismissSysDevDialog() {
        _showSysDevDialog.value = false
    }
}