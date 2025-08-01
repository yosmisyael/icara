package com.example.icara.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.icara.ui.screens.dictionary.DictionaryEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DictionaryViewModel: ViewModel() {
    private val _navigationChannel = Channel<DictionaryEntry>()
    val navigationChannel = _navigationChannel.receiveAsFlow()

    fun navigateToDictionaryDetailScreen(entry: DictionaryEntry) {
        viewModelScope.launch {
            _navigationChannel.send(entry)
        }
    }
}