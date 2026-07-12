package com.nyavo.keyboard

object KeyboardLayoutData {
    
    // Ponctuation rapide au-dessus de la barre d'espace
    val QUICK_PUNCTUATION = listOf(",", ".", "?", "!", "@", "-")

    fun rowsFor(type: KeyboardLayoutType): List<List<String>> {
        return when (type) {
            KeyboardLayoutType.AZERTY -> listOf(
                listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m"),
                listOf("w", "x", "c", "v", "b", "n")
            )
            KeyboardLayoutType.QWERTY -> listOf(
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("z", "x", "c", "v", "b", "n", "m")
            )
            KeyboardLayoutType.QWERTZ -> listOf(
                listOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("y", "x", "c", "v", "b", "n", "m")
            )
        }
    }
}
