package com.example.icara.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class HomeViewModel: ViewModel() {
    private val _showLangDialog = MutableStateFlow(false)
    private val _navigateToTalkScreen = Channel<String>()
    val navigateToTalkScreen = _navigateToTalkScreen.receiveAsFlow()
    val showLangDialog: StateFlow<Boolean> = _showLangDialog.asStateFlow()

    fun onDialogTrigger() {
        _showLangDialog.value = true
    }

    fun onDialogDismiss() {
        _showLangDialog.value = false
    }


    fun onLangSelected(lang: String) {
        _showLangDialog.value = false
        viewModelScope.launch {
            _navigateToTalkScreen.send(lang)
        }
    }

}