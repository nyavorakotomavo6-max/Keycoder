package com.nyavo.keyboard

object LongPressSymbols {

    private val MAPPING = mapOf(
        'a' to "@",
        'b' to "'",
        'c' to ":",
        'd' to "$",
        'e' to "=",
        'f' to "{",
        'g' to "}",
        'h' to "(",
        'i' to "/",
        'j' to ")",
        'k' to "[",
        'l' to "]",
        'm' to "?",
        'n' to "!",
        'o' to "\\",
        'p' to "|",
        'q' to "~",
        'r' to "+",
        's' to ";",
        't' to "-",
        'u' to "*",
        'v' to "\"",
        'w' to "`",
        'x' to ">",
        'y' to "_",
        'z' to "<"
    )

    fun symbolFor(letter: String): String? {
        if (letter.isEmpty()) return null
        return MAPPING[letter.lowercase()[0]]
    }
}