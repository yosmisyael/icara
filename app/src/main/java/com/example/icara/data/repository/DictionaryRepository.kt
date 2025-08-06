package com.example.icara.data.repository

import com.example.icara.data.network.DictionaryApiService
import com.example.icara.data.network.NetworkModule
import com.example.icara.data.model.DictionaryEntry
import com.example.icara.data.model.toDictionaryEntry
import com.example.icara.data.cache.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.io.IOException

// Custom exception classes
class ApiException(message: String) : Exception(message)
class NetworkException(message: String) : Exception(message)
class NoInternetException(message: String = "No internet connection") : Exception(message)
class TimeoutException(message: String = "Request timeout") : Exception(message)

class DictionaryRepository(
    private val apiService: DictionaryApiService = NetworkModule.dictionaryApiService,
    private val cacheManager: CacheManager = CacheManager,
) {
    /**
     * Get cached entries if available and valid
     */
    fun getCachedEntries(): List<DictionaryEntry>? {
        val cached = cacheManager.getCachedEntries()
        Log.d("DictionaryRepository", "getCachedEntries(): ${cached?.size ?: "null"} entries")
        Log.d("DictionaryRepository", "Cache info: ${cacheManager.getCacheInfo()}")
        return cached
    }

    /**
     * Get dictionary entries - will try cache first, then fetch from API if needed
     */
    suspend fun getDictionaryEntries(query: String = ""): Result<List<DictionaryEntry>> {
        return withContext(Dispatchers.IO) {
            Log.d("DictionaryRepository", "=== getDictionaryEntries() called ===")

            try {
                Log.d("DictionaryRepository", "Making API call...")
                val response = apiService.getDictionaryEntries()
                Log.d("DictionaryRepository", "API response received. Success: ${response.isSuccessful}, Code: ${response.code()}")

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    Log.d("DictionaryRepository", "Response body: data=${apiResponse?.data?.size}, error=${apiResponse?.error}")

                    if (apiResponse?.data != null) {
                        // Convert API models to UI models
                        val entries = apiResponse.data.map { it.toDictionaryEntry() }
                        Log.d("DictionaryRepository", "Converted ${entries.size} entries, caching...")

                        // Cache the fetched entries
                        cacheManager.setCachedEntries(entries)
                        Log.d("DictionaryRepository", "Entries cached successfully")

                        Result.success(entries)
                    } else {
                        val error = apiResponse?.error ?: "No data received"
                        Log.e("DictionaryRepository", "API error: $error")
                        throw ApiException(error)
                    }
                } else {
                    val error = "HTTP ${response.code()}: ${response.message()}"
                    Log.e("DictionaryRepository", "HTTP error: $error")
                    throw NetworkException(error)
                }
            } catch (e: SocketTimeoutException) {
                Log.e("DictionaryRepository", "Timeout exception: ${e.message}")
                handleExceptionWithCache(e, "Request timeout") { TimeoutException("Request timeout") }
            } catch (e: UnknownHostException) {
                Log.e("DictionaryRepository", "Unknown host exception: ${e.message}")
                handleExceptionWithCache(e, "No internet connection") { NoInternetException("No internet connection") }
            } catch (e: IOException) {
                Log.e("DictionaryRepository", "IO exception: ${e.message}")
                handleExceptionWithCache(e, "Network error") { NetworkException("Network error: ${e.message}") }
            } catch (e: Exception) {
                Log.e("DictionaryRepository", "Other exception: ${e::class.simpleName} - ${e.message}")
                handleExceptionWithCache(e, "Unknown error") { e }
            }
        }
    }

    private fun handleExceptionWithCache(
        originalException: Exception,
        errorType: String,
        exceptionFactory: () -> Exception
    ): Result<List<DictionaryEntry>> {
        Log.d("DictionaryRepository", "Handling $errorType, checking cache...")
        val cachedEntries = cacheManager.getCachedEntries()
        Log.d("DictionaryRepository", "Cache fallback: ${cachedEntries?.size ?: "null"} entries")

        return if (cachedEntries != null && cachedEntries.isNotEmpty()) {
            Log.d("DictionaryRepository", "Returning cached entries as fallback")
            Result.success(cachedEntries)
        } else {
            Log.e("DictionaryRepository", "No cache available, returning failure")
            Result.failure(exceptionFactory())
        }
    }

    /**
     * Clear the cache
     */
    fun clearCache() {
        Log.d("DictionaryRepository", "Clearing cache...")
        cacheManager.clearCache()
        Log.d("DictionaryRepository", "Cache cleared")
    }

    /**
     * Check if cache is valid
     */
    fun isCacheValid(): Boolean {
        val isValid = cacheManager.isCacheValid()
        Log.d("DictionaryRepository", "isCacheValid(): $isValid")
        return isValid
    }
}