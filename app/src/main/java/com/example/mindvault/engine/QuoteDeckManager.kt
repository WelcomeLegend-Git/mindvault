package com.example.mindvault.engine

import android.content.Context

object QuoteDeckManager {
    /**
     * Draws a random unseen item from the provided list, based on the deckId.
     * If all items have been seen, it clears the memory and reshuffles.
     * @param context Application context for SharedPreferences
     * @param deckId Unique identifier for this deck (e.g. "deck_HARSH_WAKEUP")
     * @param items The list of all items available to draw
     * @param idSelector A function that returns a unique string identifier for an item
     * @return The drawn item, or null if the items list is empty
     */
    fun <T> draw(context: Context, deckId: String, items: List<T>, idSelector: (T) -> String): T? {
        if (items.isEmpty()) return null

        val prefs = context.getSharedPreferences("mindvault_quote_decks", Context.MODE_PRIVATE)
        val seenIds = prefs.getStringSet(deckId, emptySet()) ?: emptySet()

        // Filter out seen items
        var availableItems = items.filter { idSelector(it) !in seenIds }

        // If all items have been seen (deck is empty), reshuffle
        if (availableItems.isEmpty()) {
            availableItems = items
            prefs.edit().remove(deckId).apply()
        }

        // Pick a random item from available
        val picked = availableItems.random()

        // Mark it as seen
        val newSeenIds = seenIds.toMutableSet().apply { add(idSelector(picked)) }
        prefs.edit().putStringSet(deckId, newSeenIds).apply()

        return picked
    }
}
