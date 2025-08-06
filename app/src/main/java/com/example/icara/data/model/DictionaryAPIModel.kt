package com.example.icara.data.model

import com.google.gson.annotations.SerializedName

// response wrapper
data class ApiResponse<T>(
    val data: T?,
    val error: String? = null
)

data class DictionaryEntryApi(
    val id: Int,
    val name: String,
    @SerializedName("signLang") val signLang: String,
    val url: String,
    val source: String? = null,
    val type: String,
    val aliases: List<String> = emptyList()
)

// function to convert API model to UI model
fun DictionaryEntryApi.toDictionaryEntry(): DictionaryEntry {
    return DictionaryEntry(
        id = this.id.toString(),
        name = this.name,
        signLanguage = this.signLang,
        streamUrl = this.url,
        type = when (this.type.uppercase()) {
            "WORD" -> EntryType.WORD
            "LETTER" -> EntryType.LETTER
            else -> EntryType.NUMBER
        },
        source = this.source,
        aliases = this.aliases,
    )
}