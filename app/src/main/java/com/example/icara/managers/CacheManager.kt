package com.example.icara.managers

import android.util.Log
import com.example.icara.config.AppConfig
import com.example.icara.data.model.DictionaryEntry

object CacheManager {
    private var cachedEntries: List<DictionaryEntry>? = null
    private var cacheTimestamp: Long = 0

    /**
     * Get cached entries if they exist and are still valid
     */
    fun getCachedEntries(): List<DictionaryEntry>? {
        val isValid = isCacheValid()
        Log.d("CacheManager", "getCachedEntries() - Valid: $isValid, Size: ${cachedEntries?.size}, Age: ${getCacheAgeMinutes()}min")

        return if (isValid) {
            cachedEntries
        } else {
            Log.d("CacheManager", "Cache invalid or empty, returning null")
            null
        }
    }

    /**
     * Cache the entries with current timestamp
     */
    fun setCachedEntries(entries: List<DictionaryEntry>) {
        Log.d("CacheManager", "setCachedEntries() - Caching ${entries.size} entries")
        cachedEntries = entries
        cacheTimestamp = System.currentTimeMillis()
        Log.d("CacheManager", "Cache set successfully. Timestamp: $cacheTimestamp. Entries: ${cachedEntries?.size}")
    }

    /**
     * Check if cache exists and is still within the valid duration
     */
    fun isCacheValid(): Boolean {
        val hasEntries = cachedEntries != null && cachedEntries!!.isNotEmpty()
        val currentTime = System.currentTimeMillis()
        val ageMs = currentTime - cacheTimestamp
        val isWithinDuration = ageMs < AppConfig.CACHE_DURATION_MS

        val isValid = hasEntries && isWithinDuration

        Log.d("CacheManager", """
            isCacheValid() check:
            - Has entries: $hasEntries (${cachedEntries?.size ?: "null"})
            - Current time: $currentTime
            - Cache timestamp: $cacheTimestamp
            - Age (ms): $ageMs
            - Cache duration limit: ${AppConfig.CACHE_DURATION_MS}
            - Within duration: $isWithinDuration
            - Final result: $isValid
        """.trimIndent())

        return isValid
    }

    /**
     * Clear all cached data
     */
    fun clearCache() {
        Log.d("CacheManager", "clearCache() called")
        cachedEntries = null
        cacheTimestamp = 0
        Log.d("CacheManager", "Cache cleared")
    }

    /**
     * Get cache age in minutes
     */
    private fun getCacheAgeMinutes(): Long {
        if (cacheTimestamp == 0L) return -1
        return (System.currentTimeMillis() - cacheTimestamp) / 1000 / 60
    }

    /**
     * Get cache info for debugging
     */
    fun getCacheInfo(): String {
        return if (cachedEntries != null) {
            val ageMs = System.currentTimeMillis() - cacheTimestamp
            val ageMins = ageMs / 1000 / 60
            "Cache: ${cachedEntries?.size} entries, ${ageMins}min old, valid: ${isCacheValid()}"
        } else {
            "Cache: empty"
        }
    }
}