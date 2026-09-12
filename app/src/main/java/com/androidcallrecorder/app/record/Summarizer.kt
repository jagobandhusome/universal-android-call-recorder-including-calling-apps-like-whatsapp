package com.androidcallrecorder.app.record

object Summarizer {
    fun threeLines(transcript: String): String {
        val text = transcript.replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return ""
        val sentences = text.split(Regex("(?<=[.!?।])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        if (sentences.isEmpty()) return text.take(240)
        val scored = sentences.map { s ->
            val words = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }
            s to words.size
        }.sortedByDescending { it.second }
        return scored.take(3).map { it.first }.joinToString("\n")
    }
}
