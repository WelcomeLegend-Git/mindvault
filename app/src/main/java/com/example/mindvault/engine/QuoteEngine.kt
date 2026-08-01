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

        val quoteDirectories = when (vibe) {
            ScenarioVibe.HARSH_WAKEUP -> listOf("quotes/focus_power", "quotes/telegram_quotes.json")
            ScenarioVibe.ENCOURAGING -> listOf("quotes/focus_power", "quotes/wisdom_philosophy")
            ScenarioVibe.MINDFUL_REFOCUS -> listOf("quotes/mindfulness_presence", "quotes/telegram_quotes.json")
            ScenarioVibe.HIGH_ENERGY -> listOf("quotes/focus_power")
            ScenarioVibe.REFLECTIVE_WINDDOWN -> listOf("quotes/resilience_healing")
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
        
        fun loadFromFile(path: String) {
            try {
                val inputStream = context.assets.open(path)
                val reader = InputStreamReader(inputStream)
                val listType = object : TypeToken<List<Quote>>() {}.type
                val quotes: List<Quote> = gson.fromJson(reader, listType) ?: emptyList()
                allQuotes.addAll(quotes)
                reader.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load quotes from $path", e)
            }
        }

        for (path in quoteDirectories) {
            try {
                val files = context.assets.list(path)
                if (files.isNullOrEmpty()) {
                    if (path.endsWith(".json")) {
                        loadFromFile(path)
                    }
                } else {
                    for (file in files) {
                        if (file.endsWith(".json")) {
                            loadFromFile("$path/$file")
                        }
                    }
                }
            } catch (e: Exception) {
                if (path.endsWith(".json")) {
                    loadFromFile(path)
                }
            }
        }

        val deckId = "deck_${vibe.name}"
        val selectedQuote = QuoteDeckManager.draw(context, deckId, allQuotes) { 
            // Use the quote text itself as the unique ID to ensure identical quotes aren't repeated
            it.q.hashCode().toString() 
        } ?: Quote("Put down the screen. Look up at the room. Take a deep breath.", "MindVault", "digital_detox")

        return QuoteResult(selectedQuote, font, vibe)
    }
}
