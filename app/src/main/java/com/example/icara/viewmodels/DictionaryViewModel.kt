package com.example.icara.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.icara.data.model.DictionaryEntry
import com.example.icara.data.repository.ApiException
import com.example.icara.data.repository.DictionaryRepository
import com.example.icara.data.repository.NetworkException
import com.example.icara.data.repository.NoInternetException
import com.example.icara.data.repository.TimeoutException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import android.util.Log

data class DictionaryUiState(
    val isLoading: Boolean = false,
    val entries: List<DictionaryEntry> = emptyList(),
    val allEntries: List<DictionaryEntry> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val isOffline: Boolean = false
)

class DictionaryViewModel(
    private val repository: DictionaryRepository = DictionaryRepository(),
): ViewModel() {
    private val _navigationChannel = Channel<DictionaryEntry>()
    val navigationChannel = _navigationChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    init {
        loadDictionary()
    }

    fun loadDictionary() {
        viewModelScope.launch {
            Log.d("DictionaryViewModel", "=== loadDictionary() called ===")

            // Check cache first
            val cachedEntries = repository.getCachedEntries()
            Log.d("DictionaryViewModel", "Cached entries: ${cachedEntries?.size ?: "null"}")
            Log.d("DictionaryViewModel", "Cache valid: ${repository.isCacheValid()}")

            if (cachedEntries != null && cachedEntries.isNotEmpty()) {
                Log.d("DictionaryViewModel", "Using cached entries: ${cachedEntries.size} items")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    allEntries = cachedEntries,
                    entries = filterEntries(cachedEntries, _uiState.value.searchQuery),
                    error = null,
                    isOffline = false
                )
                return@launch
            }

            Log.d("DictionaryViewModel", "No valid cache found, fetching from API...")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.getDictionaryEntries()
                .onSuccess { entries ->
                    Log.d("DictionaryViewModel", "API success: ${entries.size} entries")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allEntries = entries,
                        entries = filterEntries(entries, _uiState.value.searchQuery),
                        error = null,
                        isOffline = false
                    )
                }
                .onFailure { exception ->
                    Log.e("DictionaryViewModel", "API failed: ${exception::class.simpleName} - ${exception.message}")

                    // Check cache again after API failure
                    val fallbackCache = repository.getCachedEntries()
                    Log.d("DictionaryViewModel", "Fallback cache check: ${fallbackCache?.size ?: "null"} entries")

                    if (fallbackCache != null && fallbackCache.isNotEmpty()) {
                        Log.d("DictionaryViewModel", "Using fallback cache: ${fallbackCache.size} items")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            allEntries = fallbackCache,
                            entries = filterEntries(fallbackCache, _uiState.value.searchQuery),
                            error = null,
                            isOffline = true
                        )
                        return@onFailure
                    }

                    val errorMessage = when (exception) {
                        is NoInternetException -> "Tidak ada koneksi internet. Periksa koneksi Anda dan coba lagi."
                        is TimeoutException -> "Koneksi timeout. Periksa koneksi internet Anda."
                        is NetworkException -> "Terjadi kesalahan jaringan. Coba lagi nanti."
                        is ApiException -> "Terjadi kesalahan server: ${exception.message}"
                        else -> "Terjadi kesalahan yang tidak terduga: ${exception.message}"
                    }

                    Log.e("DictionaryViewModel", "Showing error: $errorMessage")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allEntries = emptyList(),
                        entries = emptyList(),
                        error = errorMessage,
                        isOffline = exception is NoInternetException
                    )
                }
        }
    }

    fun updateSearchQuery(query: String) {
        val currentState = _uiState.value
        val filteredEntries = filterEntries(currentState.allEntries, query)

        _uiState.value = currentState.copy(
            searchQuery = query,
            entries = filteredEntries
        )
    }

    private fun filterEntries(allEntries: List<DictionaryEntry>, query: String): List<DictionaryEntry> {
        if (query.isBlank()) {
            return allEntries
        }

        val searchQuery = query.trim().lowercase()

        return allEntries.filter { entry ->
            // Search in name
            entry.name.lowercase().contains(searchQuery) ||
                    // Search in aliases
                    entry.aliases.any { alias -> alias.lowercase().contains(searchQuery) }
        }
    }

    fun navigateToDictionaryDetailScreen(entry: DictionaryEntry) {
        viewModelScope.launch {
            _navigationChannel.send(entry)
        }
    }

    fun retry() {
        Log.d("DictionaryViewModel", "Retry called")
        loadDictionary()
    }

    fun clearCache() {
        Log.d("DictionaryViewModel", "Clearing cache")
        repository.clearCache()
        loadDictionary()
    }
}