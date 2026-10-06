package com.example.ai

object AiResponseValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    private val forbiddenPhrases = listOf(
        "as an ai",
        "as a language model",
        "i am an artificial intelligence",
        "here is your reply",
        "system prompt",
        "reply as:",
        "[insert name]",
        "internal instruction",
        "as per your instructions",
        "assistant:"
    )

    fun validate(
        rawReply: String?,
        maxCharacters: Int = 500
    ): ValidationResult {
        if (rawReply.isNullOrBlank()) {
            return ValidationResult.Invalid("AI response is empty.")
        }

        val cleaned = rawReply.trim()

        if (cleaned.length > maxCharacters + 100) {
            return ValidationResult.Invalid("AI response exceeded maximum character limit (${cleaned.length} > $maxCharacters).")
        }

        val lower = cleaned.lowercase()
        for (phrase in forbiddenPhrases) {
            if (lower.startsWith(phrase) || lower.contains(phrase)) {
                return ValidationResult.Invalid("Response contained robotic/system boilerplate: '$phrase'")
            }
        }

        // Check if response looks like an API error JSON or stacktrace
        if (cleaned.startsWith("{") && cleaned.contains("\"error\"")) {
            return ValidationResult.Invalid("Malformed AI response containing raw error payload.")
        }

        return ValidationResult.Valid
    }

    fun cleanResponse(raw: String): String {
        var text = raw.trim()
        // Strip surrounding quotes if the model wrapped the entire dialogue in quotes
        if (text.startsWith("\"") && text.endsWith("\"") && text.length > 2) {
            text = text.substring(1, text.length - 1).trim()
        }
        // Strip "Me: " or "You: " prefix if model included speaker tag
        if (text.startsWith("Me:", ignoreCase = true)) {
            text = text.substring(3).trim()
        }
        if (text.startsWith("Reply:", ignoreCase = true)) {
            text = text.substring(6).trim()
        }
        return text
    }
}
