package com.xdown.app.util

object StringUtils {
    fun extractTweetId(url: String): String? {
        if (url.trim().matches(Regex("\\d{1,25}"))) return url.trim()
        val pattern = """(?:x\.com|twitter\.com)/[^/]+/status(?:es)?/(\d{1,25})(?:[^0-9]|$)""".toRegex()
        return pattern.find(url)?.groupValues?.get(1)
    }

    fun extractUsername(input: String): String {
        return input.trimStart('@').trim()
    }

    fun isValidTweetUrl(url: String): Boolean {
        val pattern = """(?:https?://)?(?:mobile\.)?(?:x|twitter)\.com/[^/]+/status(?:es)?/\d{1,25}(?:[?#].*)?""".toRegex()
        return pattern.matches(url.trim())
    }

    fun isValidUsername(username: String): Boolean {
        val pattern = """^@?\w{1,15}$""".toRegex()
        return pattern.matches(username.trim())
    }

    fun normalizeInput(input: String): String {
        val trimmed = input.trim()
        if (isValidTweetUrl(trimmed)) return trimmed
        if (isValidUsername(trimmed)) return extractUsername(trimmed)
        return trimmed
    }
}
