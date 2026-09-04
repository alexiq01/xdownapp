package com.xdown.app.util

object StringUtils {
    fun extractTweetId(url: String): String? {
        val pattern = """(?:x\.com|twitter\.com)/\w+/status/(\d+)""".toRegex()
        return pattern.find(url)?.groupValues?.get(1)
    }

    fun extractUsername(input: String): String {
        return input.trimStart('@').trim()
    }

    fun isValidTweetUrl(url: String): Boolean {
        val pattern = """https?://(?:x\.com|twitter\.com)/\w+/status/\d+.*""".toRegex()
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
