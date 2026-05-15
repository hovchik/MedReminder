package med.reminder.com.ai

import java.util.Locale

/**
 * Provides language instructions for AI prompts based on the user's selected app locale.
 * This ensures AI responses are generated in the same language as the app UI.
 */
object AiLanguageHelper {

    private val languageNames = mapOf(
        "en" to "English",
        "ru" to "Russian",
        "es" to "Spanish",
        "zh" to "Chinese (Simplified)",
        "hy" to "Armenian",
        "fa" to "Persian (Farsi)",
        "ja" to "Japanese",
        "hi" to "Hindi"
    )

    /**
     * Returns the full language name for the current app locale.
     * Falls back to English if the locale is not in the supported list.
     */
    fun getCurrentLanguageName(): String {
        val langCode = Locale.getDefault().language
        return languageNames[langCode] ?: "English"
    }

    /**
     * Returns a prompt instruction that tells the AI to respond in the user's language.
     * Returns an empty string if the language is English (no instruction needed).
     */
    fun getLanguageInstruction(): String {
        val langCode = Locale.getDefault().language
        if (langCode == "en") return ""
        val langName = languageNames[langCode] ?: return ""
        return "\n\nIMPORTANT: You MUST write ALL your response text (summary, insights, recommendations, descriptions, tips, warnings, etc.) in $langName. Keep JSON keys in English but all values must be in $langName."
    }
}
