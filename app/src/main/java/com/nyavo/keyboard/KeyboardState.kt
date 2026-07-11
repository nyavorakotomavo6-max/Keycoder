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
    LETTERS, EMOJI
}

class KeyboardState(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var layoutType: KeyboardLayoutType = loadLayoutType()
        private set

    var shiftState: ShiftState = ShiftState.OFF
    var mode: KeyboardMode = KeyboardMode.LETTERS

    private var lastShiftTapTime: Long = 0L

    fun cycleLayout(): KeyboardLayoutType {
        layoutType = layoutType.next()
        saveLayoutType(layoutType)
        return layoutType
    }

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

    companion object {
        private const val PREFS_NAME = "nyavo_keyboard_prefs"
        private const val KEY_LAYOUT = "keyboard_layout_type"
        private const val DOUBLE_TAP_WINDOW_MS = 300L
    }
}