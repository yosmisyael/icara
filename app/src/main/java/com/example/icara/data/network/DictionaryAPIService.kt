package com.example.icara.data.network

import com.example.icara.data.model.DictionaryEntryApi
import com.example.icara.data.model.ApiResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface DictionaryApiService {
    @GET("dictionary")
    suspend fun getDictionaryEntries(): Response<ApiResponse<List<DictionaryEntryApi>>>

    @GET("dictionary/{id}")
    suspend fun getDictionaryEntryById(
        @Path("id") id: String
    ): Response<ApiResponse<DictionaryEntryApi>>
}
