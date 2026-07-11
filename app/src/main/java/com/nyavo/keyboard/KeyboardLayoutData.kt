package com.nyavo.keyboard

object KeyboardLayoutData {

    private val AZERTY_ROWS = listOf(
        listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m"),
        listOf("w", "x", "c", "v", "b", "n")
    )

    private val QWERTY_ROWS = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m")
    )

    private val QWERTZ_ROWS = listOf(
        listOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("y", "x", "c", "v", "b", "n", "m")
    )

    fun rowsFor(type: KeyboardLayoutType): List<List<String>> = when (type) {
        KeyboardLayoutType.AZERTY -> AZERTY_ROWS
        KeyboardLayoutType.QWERTY -> QWERTY_ROWS
        KeyboardLayoutType.QWERTZ -> QWERTZ_ROWS
    }

    val QUICK_PUNCTUATION = listOf(",", ".", "?", "!", "'", "-")
}