package com.nyavo.keyboard

object EmojiData {

    data class EmojiCategory(val label: String, val emojis: List<String>)

    val CATEGORIES = listOf(
        EmojiCategory(
            label = "Smileys",
            emojis = listOf(
                "😀", "😁", "😂", "🤣", "😅", "😊", "😉", "😍",
                "😘", "😎", "🤔", "😴", "😢", "😭", "😡", "🥳"
            )
        ),
        EmojiCategory(
            label = "Animaux",
            emojis = listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
                "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔"
            )
        ),
        EmojiCategory(
            label = "Nourriture",
            emojis = listOf(
                "🍏", "🍌", "🍇", "🍓", "🍕", "🍔", "🍟", "🌭",
                "🍿", "🍩", "🍪", "🍫", "☕", "🍵", "🍺", "🍎"
            )
        ),
        EmojiCategory(
            label = "Objets",
            emojis = listOf(
                "📱", "💻", "⌨", "🖱", "🔋", "💡", "🔧", "🔑",
                "📌", "📎", "✏", "📚", "🎧", "📷", "⏰", "🔒"
            )
        ),
        EmojiCategory(
            label = "Symboles",
            emojis = listOf(
                "❤", "⭐", "✅", "❌", "⚡", "🔥", "💯", "❓",
                "❗", "➡", "⬅", "🔄", "📍", "🔔", "✔", "➕"
            )
        )
    )
}