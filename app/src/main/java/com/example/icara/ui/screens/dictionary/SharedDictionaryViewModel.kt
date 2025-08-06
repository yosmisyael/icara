package com.example.icara.ui.screens.dictionary

import androidx.lifecycle.ViewModel
import com.example.icara.data.model.DictionaryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedDictionaryViewModel: ViewModel() {
    private val _selectedEntry = MutableStateFlow<DictionaryEntry?>(null)
    val selectedEntry: StateFlow<DictionaryEntry?> = _selectedEntry.asStateFlow()

    fun setSelectedEntry(entry: DictionaryEntry) {
        _selectedEntry.value = entry
    }

    fun clearSelectedEntry() {
        _selectedEntry.value = null
    }
}