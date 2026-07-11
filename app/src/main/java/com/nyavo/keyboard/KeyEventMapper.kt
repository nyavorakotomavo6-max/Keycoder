package com.nyavo.keyboard

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * Traduit les touches du Mode Code en vrais événements clavier Android
 * (KeyEvent avec méta-états) plutôt qu'en simple insertion de texte.
 * Nécessaire pour que les raccourcis (Ctrl+C, Ctrl+/, Shift+Tab...)
 * soient reconnus par des applications comme Termux ou Acode, qui
 * écoutent les événements clavier bruts et pas seulement le texte
 * committé.
 */
object KeyEventMapper {

    private val SPECIAL_KEYCODES = mapOf(
        "Tab" to KeyEvent.KEYCODE_TAB,
        "Esc" to KeyEvent.KEYCODE_ESCAPE,
        "←" to KeyEvent.KEYCODE_DPAD_LEFT,
        "→" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "↑" to KeyEvent.KEYCODE_DPAD_UP,
        "↓" to KeyEvent.KEYCODE_DPAD_DOWN
    )

    private val LETTER_KEYCODES = mapOf(
        'a' to KeyEvent.KEYCODE_A,
        'b' to KeyEvent.KEYCODE_B,
        'c' to KeyEvent.KEYCODE_C,
        'd' to KeyEvent.KEYCODE_D,
        'e' to KeyEvent.KEYCODE_E,
        'f' to KeyEvent.KEYCODE_F,
        'g' to KeyEvent.KEYCODE_G,
        'h' to KeyEvent.KEYCODE_H,
        'i' to KeyEvent.KEYCODE_I,
        'j' to KeyEvent.KEYCODE_J,
        'k' to KeyEvent.KEYCODE_K,
        'l' to KeyEvent.KEYCODE_L,
        'm' to KeyEvent.KEYCODE_M,
        'n' to KeyEvent.KEYCODE_N,
        'o' to KeyEvent.KEYCODE_O,
        'p' to KeyEvent.KEYCODE_P,
        'q' to KeyEvent.KEYCODE_Q,
        'r' to KeyEvent.KEYCODE_R,
        's' to KeyEvent.KEYCODE_S,
        't' to KeyEvent.KEYCODE_T,
        'u' to KeyEvent.KEYCODE_U,
        'v' to KeyEvent.KEYCODE_V,
        'w' to KeyEvent.KEYCODE_W,
        'x' to KeyEvent.KEYCODE_X,
        'y' to KeyEvent.KEYCODE_Y,
        'z' to KeyEvent.KEYCODE_Z
    )

    private val PUNCTUATION_KEYCODES = mapOf(
        '[' to KeyEvent.KEYCODE_LEFT_BRACKET,
        ']' to KeyEvent.KEYCODE_RIGHT_BRACKET,
        ';' to KeyEvent.KEYCODE_SEMICOLON,
        '\'' to KeyEvent.KEYCODE_APOSTROPHE,
        '`' to KeyEvent.KEYCODE_GRAVE,
        '\\' to KeyEvent.KEYCODE_BACKSLASH,
        '/' to KeyEvent.KEYCODE_SLASH,
        '.' to KeyEvent.KEYCODE_PERIOD,
        ',' to KeyEvent.KEYCODE_COMMA,
        '-' to KeyEvent.KEYCODE_MINUS,
        '=' to KeyEvent.KEYCODE_EQUALS
    )

    fun keyCodeForSpecial(label: String): Int? = SPECIAL_KEYCODES[label]

    fun keyCodeForLetter(letter: Char): Int? = LETTER_KEYCODES[letter.lowercaseChar()]

    fun keyCodeForPunctuation(symbol: Char): Int? = PUNCTUATION_KEYCODES[symbol]

    fun buildMetaState(ctrl: Boolean, alt: Boolean, shift: Boolean): Int {
        var meta = 0
        if (ctrl) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (alt) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (shift) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        return meta
    }

    fun dispatch(ic: InputConnection, keyCode: Int, metaState: Int) {
        val now = System.currentTimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState)
        ic.sendKeyEvent(down)
        ic.sendKeyEvent(up)
    }
}