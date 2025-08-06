package com.example.icara.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class EntryType {
    LETTER,
    WORD,
    NUMBER,
}

@Parcelize
data class DictionaryEntry(
    val id: String,
    val name: String,
    val signLanguage: String,
    val streamUrl: String,
    val source: String? = null,
    val type: EntryType,
    val aliases: List<String>,
): Parcelable