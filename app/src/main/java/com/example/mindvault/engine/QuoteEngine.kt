package com.example.mindvault.engine

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.util.Calendar
import kotlin.random.Random

data class Quote(
    val q: String,
    val a: String = "Unknown",
    val cat: String = "general"
)

enum class ScenarioVibe {
    HARSH_WAKEUP,       // Doomscrolling at night
    ENCOURAGING,        // Studying at night
    MINDFUL_REFOCUS,    // Doomscrolling in day
    HIGH_ENERGY,        // Studying in day
    REFLECTIVE_WINDDOWN // Idle at night
}

data class QuoteResult(
    val quote: Quote,
    val fontFileName: String,
    val vibe: ScenarioVibe
)

object QuoteEngine {
    private const val TAG = "QuoteEngine"
    private val gson = Gson()
    private val random = Random(System.currentTimeMillis())

    // 5 Typography Options from Google Fonts
    private val fonts = listOf(
        "bodoni_moda_italic.ttf",
        "cormorant_garamond_italic.ttf",
        "caveat_semibold.ttf",
        "italiana_regular.ttf",
        "merriweather_bold_italic.ttf"
    )

    fun getQuoteForContext(
        context: Context,
        isStudySessionActive: Boolean,
        isScrollingSocialMedia: Boolean
    ): QuoteResult {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = hour >= 22 || hour < 5 // 10 PM to 5 AM

        // Determine Scenario Vibe
        val vibe = when {
            isNight && isScrollingSocialMedia -> ScenarioVibe.HARSH_WAKEUP
            isNight && isStudySessionActive -> ScenarioVibe.ENCOURAGING
            !isNight && isScrollingSocialMedia -> ScenarioVibe.MINDFUL_REFOCUS
            !isNight && isStudySessionActive -> ScenarioVibe.HIGH_ENERGY
            else -> ScenarioVibe.REFLECTIVE_WINDDOWN
        }

        // Determine matching Quote Files/Categories (Can load dynamically, but hardcoded mappings for now)
        val quoteFiles = when (vibe) {
            ScenarioVibe.HARSH_WAKEUP -> listOf(
                "quotes/focus_power/gemini-code-1785566763527.json", 
                "quotes/telegram_quotes.json"
            )
            ScenarioVibe.ENCOURAGING -> listOf(
                "quotes/focus_power/aaasdf.json",
                "quotes/wisdom_philosophy/gemini-code-1785571941483.json"
            )
            ScenarioVibe.MINDFUL_REFOCUS -> listOf(
                "quotes/mindfulness_presence/gemini-code-1785571562873.json",
                "quotes/telegram_quotes.json"
            )
            ScenarioVibe.HIGH_ENERGY -> listOf(
                "quotes/focus_power/askddfj.json"
            )
            ScenarioVibe.REFLECTIVE_WINDDOWN -> listOf(
                "quotes/resilience_healing/gemini-code-1785571644654.json"
            )
        }

        // Select a Font matching the vibe
        val font = when (vibe) {
            ScenarioVibe.HARSH_WAKEUP -> listOf("bodoni_moda_italic.ttf", "caveat_semibold.ttf").random()
            ScenarioVibe.ENCOURAGING -> listOf("cormorant_garamond_italic.ttf", "merriweather_bold_italic.ttf").random()
            ScenarioVibe.MINDFUL_REFOCUS -> listOf("italiana_regular.ttf", "bodoni_moda_italic.ttf").random()
            ScenarioVibe.HIGH_ENERGY -> listOf("merriweather_bold_italic.ttf", "cormorant_garamond_italic.ttf").random()
            ScenarioVibe.REFLECTIVE_WINDDOWN -> listOf("caveat_semibold.ttf", "italiana_regular.ttf").random()
        }

        val allQuotes = mutableListOf<Quote>()
        for (file in quoteFiles) {
            try {
                val inputStream = context.assets.open(file)
                val reader = InputStreamReader(inputStream)
                val listType = object : TypeToken<List<Quote>>() {}.type
                val quotes: List<Quote> = gson.fromJson(reader, listType) ?: emptyList()
                allQuotes.addAll(quotes)
                reader.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load quotes from $file", e)
            }
        }

        // Fallback if list is empty due to missing files or parsing errors
        val selectedQuote = if (allQuotes.isNotEmpty()) {
            allQuotes.random(random)
        } else {
            Quote("Put down the screen. Look up at the room. Take a deep breath.", "MindVault", "digital_detox")
        }

        return QuoteResult(selectedQuote, font, vibe)
    }
}
