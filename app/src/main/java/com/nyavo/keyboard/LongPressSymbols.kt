package com.nyavo.keyboard

object LongPressSymbols {

    private val symbolsMap = mapOf(

        // Lettres accentuées
        "a" to listOf(
            "à", "â", "ä", "æ", "á", "ã"
        ),

        "e" to listOf(
            "é", "è", "ê", "ë", "ē"
        ),

        "i" to listOf(
            "î", "ï", "í", "ī"
        ),

        "o" to listOf(
            "ô", "ö", "ò", "ó", "œ", "õ"
        ),

        "u" to listOf(
            "ù", "û", "ü", "ú", "ū"
        ),

        "c" to listOf(
            "ç", "ć"
        ),

        "n" to listOf(
            "ñ", "ń"
        ),


        // Symboles
        "." to listOf(
            "…",
            "!",
            "?",
            ":",
            ";"
        ),

        "," to listOf(
            "،",
            "‚",
            "«",
            "»"
        ),

        "?" to listOf(
            "¿",
            "⁇",
            "⁈"
        ),

        "!" to listOf(
            "¡",
            "‼"
        ),

        "-" to listOf(
            "_",
            "–",
            "—"
        ),

        "@" to listOf(
            "#",
            "$",
            "%",
            "&"
        )
    )


    /**
     * Ancienne fonction conservée
     * pour éviter les erreurs de compilation
     */
    fun symbolFor(
        key: String
    ): String? {

        return symbolsMap[key]
            ?.firstOrNull()

    }



    /**
     * Nouvelle fonction pour le popup
     */
    fun symbolsFor(
        key: String
    ): List<String> {

        return symbolsMap[key]
            ?: emptyList()

    }



    /**
     * Vérifie si une touche possède
     * plusieurs caractères alternatifs
     */
    fun hasAlternatives(
        key: String
    ): Boolean {

        return symbolsMap[key]
            ?.size ?: 0 > 1

    }
}