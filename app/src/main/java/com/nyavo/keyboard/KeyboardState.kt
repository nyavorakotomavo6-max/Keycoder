package com.nyavo.keyboard

import android.content.Context
import android.content.SharedPreferences

enum class KeyboardLayoutType {
    AZERTY, QWERTY, QWERTZ;

    fun next(): KeyboardLayoutType {
        val values = entries
        val nextIndex = (this.ordinal + 1) % values.size
        return values[nextIndex]
    }
}

enum class ShiftState {
    OFF, SHIFT, CAPS_LOCK
}

enum class KeyboardMode {
    LETTERS, EMOJI, CODE
}

/**
 * État léger du clavier : pas un ViewModel Android (un IME n'a pas de
 * ViewModelStoreOwner exploitable), juste une classe simple qui centralise
 * l'état runtime (mode, shift, modificateurs Code) et l'état persistant
 * (layout choisi, mode par défaut au démarrage).
 */
class KeyboardState(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var layoutType: KeyboardLayoutType = loadLayoutType()
        private set

    var mode: KeyboardMode = loadDefaultMode()

    var shiftState: ShiftState = ShiftState.OFF

    var ctrlArmed: Boolean = false
        private set
    var altArmed: Boolean = false
        private set
    var shiftMetaArmed: Boolean = false
        private set

    private var lastShiftTapTime: Long = 0L

    // ---------------------------------------------------------------
    // Layout clavier lettres
    // ---------------------------------------------------------------

    fun cycleLayout(): KeyboardLayoutType {
        layoutType = layoutType.next()
        saveLayoutType(layoutType)
        return layoutType
    }

    // ---------------------------------------------------------------
    // Majuscule / verrouillage majuscule (mode Lettres)
    // ---------------------------------------------------------------

    fun onShiftTapped(): ShiftState {
        val now = System.currentTimeMillis()
        val isDoubleTap = (now - lastShiftTapTime) < DOUBLE_TAP_WINDOW_MS
        lastShiftTapTime = now

        shiftState = if (isDoubleTap) {
            ShiftState.CAPS_LOCK
        } else {
            when (shiftState) {
                ShiftState.OFF -> ShiftState.SHIFT
                ShiftState.SHIFT -> ShiftState.OFF
                ShiftState.CAPS_LOCK -> ShiftState.OFF
            }
        }
        return shiftState
    }

    fun consumeShiftAfterLetter() {
        if (shiftState == ShiftState.SHIFT) {
            shiftState = ShiftState.OFF
        }
    }

    fun isUppercase(): Boolean = shiftState != ShiftState.OFF

    // ---------------------------------------------------------------
    // Mode Normal <-> Code
    // ---------------------------------------------------------------

    fun toggleNormalCode(): KeyboardMode {
        mode = if (mode == KeyboardMode.CODE) KeyboardMode.LETTERS else KeyboardMode.CODE
        return mode
    }

    fun lockCurrentModeAsDefault() {
        saveDefaultMode(mode)
    }

    // ---------------------------------------------------------------
    // Modificateurs Ctrl / Alt / Shift (mode Code, KeyEvent réels)
    // ---------------------------------------------------------------

    fun toggleCtrl(): Boolean {
        ctrlArmed = !ctrlArmed
        return ctrlArmed
    }

    fun toggleAlt(): Boolean {
        altArmed = !altArmed
        return altArmed
    }

    fun toggleShiftMeta(): Boolean {
        shiftMetaArmed = !shiftMetaArmed
        return shiftMetaArmed
    }

    fun hasActiveModifiers(): Boolean = ctrlArmed || altArmed || shiftMetaArmed

    /**
     * Calcule le méta-état combiné à partir des modificateurs armés puis
     * les réinitialise (comportement "sticky one-shot" : Ctrl/Alt/Shift
     * s'appliquent à la prochaine touche seulement).
     */
    fun consumeModifiers(): Int {
        val meta = KeyEventMapper.buildMetaState(ctrlArmed, altArmed, shiftMetaArmed)
        ctrlArmed = false
        altArmed = false
        shiftMetaArmed = false
        return meta
    }

    // ---------------------------------------------------------------
    // Persistance
    // ---------------------------------------------------------------

    private fun loadLayoutType(): KeyboardLayoutType {
        val name = prefs.getString(KEY_LAYOUT, KeyboardLayoutType.QWERTY.name)
        return try {
            KeyboardLayoutType.valueOf(name ?: KeyboardLayoutType.QWERTY.name)
        } catch (e: IllegalArgumentException) {
            KeyboardLayoutType.QWERTY
        }
    }

    private fun saveLayoutType(type: KeyboardLayoutType) {
        prefs.edit().putString(KEY_LAYOUT, type.name).apply()
    }

    private fun loadDefaultMode(): KeyboardMode {
        val name = prefs.getString(KEY_DEFAULT_MODE, KeyboardMode.LETTERS.name)
        return try {
            val loaded = KeyboardMode.valueOf(name ?: KeyboardMode.LETTERS.name)
            if (loaded == KeyboardMode.EMOJI) KeyboardMode.LETTERS else loaded
        } catch (e: IllegalArgumentException) {
            KeyboardMode.LETTERS
        }
    }

    private fun saveDefaultMode(mode: KeyboardMode) {
        if (mode == KeyboardMode.EMOJI) return
        prefs.edit().putString(KEY_DEFAULT_MODE, mode.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "nyavo_keyboard_prefs"
        private const val KEY_LAYOUT = "keyboard_layout_type"
        private const val KEY_DEFAULT_MODE = "keyboard_default_mode"
        private const val DOUBLE_TAP_WINDOW_MS = 300L
    }
}