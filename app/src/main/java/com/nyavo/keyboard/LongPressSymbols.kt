package com.nyavo.keyboard

object LongPressSymbols {


    // ==============================
    // QWERTY
    // ==============================

    private val qwertySymbols = mapOf(

        "q" to listOf("1", "@", "#", "_"),
        "w" to listOf("2", "!", "&", "~"),
        "e" to listOf("3", "=", "+", "€"),
        "r" to listOf("4", "{", "}", "["),
        "t" to listOf("5", "(", ")", "]"),
        "y" to listOf("6", "<", ">", "≤"),
        "u" to listOf("7", "-", "_", "→"),
        "i" to listOf("8", "*", "/", "|"),
        "o" to listOf("9", ":", ";", "°"),
        "p" to listOf("0", "%", "^", "π"),

        "a" to listOf("@", "#", "$", "&"),
        "s" to listOf("=", "+", "-", "~"),
        "d" to listOf("{", "}", "[", "]"),
        "f" to listOf("(", ")", "<", ">"),
        "g" to listOf("/", "\\", "|", "*"),
        "h" to listOf(":", ";", "'", "\""),
        "j" to listOf("!", "?", "¿", "¡"),
        "k" to listOf("%", "°", "‰"),
        "l" to listOf("$", "€", "£"),

        "z" to listOf("1", "2", "3"),
        "x" to listOf("4", "5", "6"),
        "c" to listOf("7", "8", "9"),
        "v" to listOf("0", "+", "-"),
        "b" to listOf("=", "==", "!="),
        "n" to listOf("//", "/*", "*/"),
        "m" to listOf("_", "$", "#")
    )



    // ==============================
    // AZERTY
    // ==============================

    private val azertySymbols = mapOf(

        "a" to listOf("1", "@", "#", "_"),
        "z" to listOf("2", "!", "&", "~"),
        "e" to listOf("3", "=", "+", "€"),
        "r" to listOf("4", "{", "}", "["),
        "t" to listOf("5", "(", ")", "]"),
        "y" to listOf("6", "<", ">", "≤"),
        "u" to listOf("7", "-", "_", "→"),
        "i" to listOf("8", "*", "/", "|"),
        "o" to listOf("9", ":", ";", "°"),
        "p" to listOf("0", "%", "^", "π"),

        "q" to listOf("@", "#", "$", "&"),
        "s" to listOf("=", "+", "-", "~"),
        "d" to listOf("{", "}", "[", "]"),
        "f" to listOf("(", ")", "<", ">"),
        "g" to listOf("/", "\\", "|", "*"),
        "h" to listOf(":", ";", "'", "\""),
        "j" to listOf("!", "?", "¿", "¡"),
        "k" to listOf("%", "°", "‰"),
        "l" to listOf("$", "€", "£"),
        "m" to listOf("_", "$", "#"),

        "w" to listOf("1", "2", "3"),
        "x" to listOf("4", "5", "6"),
        "c" to listOf("7", "8", "9"),
        "v" to listOf("0", "+", "-"),
        "b" to listOf("=", "==", "!="),
        "n" to listOf("//", "/*", "*/")
    )



    // ==============================
    // QWERTZ
    // ==============================

    private val qwertzSymbols = mapOf(

        "q" to listOf("1", "@", "#", "_"),
        "w" to listOf("2", "!", "&", "~"),
        "e" to listOf("3", "=", "+", "€"),
        "r" to listOf("4", "{", "}", "["),
        "t" to listOf("5", "(", ")", "]"),
        "z" to listOf("6", "<", ">", "≤"),
        "u" to listOf("7", "-", "_", "→"),
        "i" to listOf("8", "*", "/", "|"),
        "o" to listOf("9", ":", ";", "°"),
        "p" to listOf("0", "%", "^", "π"),

        "a" to listOf("@", "#", "$", "&"),
        "s" to listOf("=", "+", "-", "~"),
        "d" to listOf("{", "}", "[", "]"),
        "f" to listOf("(", ")", "<", ">"),
        "g" to listOf("/", "\\", "|", "*"),
        "h" to listOf(":", ";", "'", "\""),
        "j" to listOf("!", "?", "¿", "¡"),
        "k" to listOf("%", "°", "‰"),
        "l" to listOf("$", "€", "£"),

        "y" to listOf("1", "2", "3"),
        "x" to listOf("4", "5", "6"),
        "c" to listOf("7", "8", "9"),
        "v" to listOf("0", "+", "-"),
        "b" to listOf("=", "==", "!="),
        "n" to listOf("//", "/*", "*/"),
        "m" to listOf("_", "$", "#")
    )



    // ==============================
    // Fonctions publiques
    // ==============================


    fun symbolsFor(
        key: String,
        layout: KeyboardLayoutType
    ): List<String> {

        return when(layout) {

            KeyboardLayoutType.QWERTY ->
                qwertySymbols[key]

            KeyboardLayoutType.AZERTY ->
                azertySymbols[key]

            KeyboardLayoutType.QWERTZ ->
                qwertzSymbols[key]
        } ?: emptyList()
    }



    // Ancienne fonction gardée
    // pour éviter les erreurs ailleurs

    fun symbolFor(
        key: String
    ): String? {

        return qwertySymbols[key]
            ?.firstOrNull()
    }



    fun hasAlternatives(
        key: String,
        layout: KeyboardLayoutType
    ): Boolean {

        return symbolsFor(key, layout).size > 1
    }
}