package com.nyavo.keyboard

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class NyavoInputMethodService : InputMethodService() {

    private lateinit var state: KeyboardState
    private var rootContainer: LinearLayout? = null

    private var shiftButton: Button? = null
    private var ctrlButton: Button? = null
    private var altButton: Button? = null
    private var shiftMetaButton: Button? = null

    private var currentEmojiCategoryIndex = 0

    override fun onCreate() {
        super.onCreate()
        state = KeyboardState(this)
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        rootContainer = root
        render()
        return root
    }

    // ---------------------------------------------------------------
    // Rendu
    // ---------------------------------------------------------------

    private fun render() {
        val root = rootContainer ?: return
        root.removeAllViews()

        shiftButton = null
        ctrlButton = null
        altButton = null
        shiftMetaButton = null

        val content = when (state.mode) {
            KeyboardMode.LETTERS -> buildLettersView()
            KeyboardMode.EMOJI -> buildEmojiView()
            KeyboardMode.CODE -> buildCodeView()
        }
        root.addView(content)

        when (state.mode) {
            KeyboardMode.LETTERS -> updateShiftButtonStyle()
            KeyboardMode.CODE -> updateModifierButtonStyles()
            KeyboardMode.EMOJI -> { /* rien à mettre à jour */ }
        }
    }

    // ---------------------------------------------------------------
    // Mode Lettres
    // ---------------------------------------------------------------

    private fun buildLettersView(): View {
        val container = verticalContainer()

        val rows = KeyboardLayoutData.rowsFor(state.layoutType)

        container.addView(buildLetterRow(rows[0]))
        container.addView(buildLetterRow(rows[1]))
        container.addView(buildThirdLetterRow(rows[2]))
        container.addView(buildPunctuationRow())
        container.addView(buildLettersBottomRow())

        return container
    }

    private fun buildLetterRow(letters: List<String>): View {
        val row = horizontalRow()
        for (letter in letters) {
            row.addView(makeKeyButton(letter.uppercase(), 1f) { handleLetterTap(letter) })
        }
        return row
    }

    private fun buildThirdLetterRow(letters: List<String>): View {
        val row = horizontalRow()

        val shift = makeKeyButton("⇧", 1.5f) { handleShiftTap() }
        shiftButton = shift
        row.addView(shift)

        for (letter in letters) {
            row.addView(makeKeyButton(letter.uppercase(), 1f) { handleLetterTap(letter) })
        }

        row.addView(makeKeyButton("⌫", 1.5f) { handleBackspace() })
        return row
    }

    private fun buildPunctuationRow(): View {
        val row = horizontalRow()
        for (symbol in KeyboardLayoutData.QUICK_PUNCTUATION) {
            row.addView(makeKeyButton(symbol, 1f) { handleSymbolTap(symbol) })
        }
        return row
    }

    private fun buildLettersBottomRow(): View {
        val row = horizontalRow()
        row.addView(makeKeyButton("😊", 1.2f) { switchToEmojiMode() })
        row.addView(makeKeyButton(layoutAbbreviation(state.layoutType), 1.2f) { cycleLayout() })
        row.addView(
            makeKeyButton(
                "Code",
                1.2f,
                onLongClick = { lockModeAndNotify() }
            ) { toggleNormalCodeMode() }
        )
        row.addView(makeKeyButton("espace", 3.4f) { handleSpace() })
        row.addView(makeKeyButton("↵", 2f) { handleEnter() })
        return row
    }

    // ---------------------------------------------------------------
    // Mode Emoji
    // ---------------------------------------------------------------

    private fun buildEmojiView(): View {
        val container = verticalContainer()
        container.addView(buildEmojiCategoryTabs())
        container.addView(buildEmojiGrid())
        container.addView(buildEmojiBottomRow())
        return container
    }

    private fun buildEmojiCategoryTabs(): View {
        val row = horizontalRow()
        EmojiData.CATEGORIES.forEachIndexed { index, category ->
            val label = category.label.take(4)
            row.addView(makeKeyButton(label, 1f, heightDp = 40) { selectEmojiCategory(index) })
        }
        return row
    }

    private fun buildEmojiGrid(): View {
        val container = verticalContainer()

        val category = EmojiData.CATEGORIES[currentEmojiCategoryIndex]
        val emojiRows = category.emojis.chunked(4)

        for (emojiRow in emojiRows) {
            val row = horizontalRow()
            for (emoji in emojiRow) {
                row.addView(makeKeyButton(emoji, 1f, heightDp = 48) { handleEmojiTap(emoji) })
            }
            val missing = 4 - emojiRow.size
            for (i in 0 until missing) {
                row.addView(makeSpacer(1f))
            }
            container.addView(row)
        }

        return container
    }

    private fun buildEmojiBottomRow(): View {
        val row = horizontalRow()
        row.addView(makeKeyButton("ABC", 1.5f) { switchToLettersMode() })
        row.addView(makeKeyButton("⌫", 1.5f) { handleBackspace() })
        row.addView(makeKeyButton("espace", 4f) { handleSpace() })
        return row
    }

    // ---------------------------------------------------------------
    // Mode Code
    // ---------------------------------------------------------------

    private fun buildCodeView(): View {
        val container = verticalContainer()

        container.addView(buildCodeModifierRow())
        container.addView(buildCodeArrowRow())
        for (symbolRow in CodeLayoutData.SYMBOL_ROWS) {
            container.addView(buildCodeSymbolRow(symbolRow))
        }
        container.addView(buildCodeBottomRow())

        return container
    }

    private fun buildCodeModifierRow(): View {
        val row = horizontalRow()

        for (label in CodeLayoutData.MODIFIER_ROW) {
            val button = when (label) {
                "Ctrl" -> makeKeyButton(label, 1f) { handleCtrlTap() }
                "Alt" -> makeKeyButton(label, 1f) { handleAltTap() }
                "Shift" -> makeKeyButton(label, 1f) { handleShiftMetaTap() }
                else -> makeKeyButton(label, 1f) { performCodeAction(label) }
            }
            when (label) {
                "Ctrl" -> ctrlButton = button
                "Alt" -> altButton = button
                "Shift" -> shiftMetaButton = button
            }
            row.addView(button)
        }

        return row
    }

    private fun buildCodeArrowRow(): View {
        val row = horizontalRow()
        for (arrow in CodeLayoutData.ARROW_ROW) {
            row.addView(makeKeyButton(arrow, 1f) { performCodeAction(arrow) })
        }
        return row
    }

    private fun buildCodeSymbolRow(symbols: List<String>): View {
        val row = horizontalRow()
        for (symbol in symbols) {
            row.addView(makeKeyButton(symbol, 1f) { performCodeAction(symbol) })
        }
        return row
    }

    private fun buildCodeBottomRow(): View {
        val row = horizontalRow()
        row.addView(
            makeKeyButton(
                "ABC",
                1.5f,
                onLongClick = { lockModeAndNotify() }
            ) { toggleNormalCodeMode() }
        )
        row.addView(makeKeyButton("⌫", 1.5f) { handleBackspace() })
        row.addView(makeKeyButton("espace", 3f) { handleSpace() })
        row.addView(makeKeyButton("↵", 1.5f) { handleEnter() })
        return row
    }

    // ---------------------------------------------------------------
    // Handlers — texte simple
    // ---------------------------------------------------------------

    private fun handleLetterTap(letter: String) {
        val ic = currentInputConnection ?: return

        if (state.hasActiveModifiers()) {
            val keyCode = KeyEventMapper.keyCodeForLetter(letter[0])
            val meta = state.consumeModifiers()
            if (keyCode != null) {
                KeyEventMapper.dispatch(ic, keyCode, meta)
            } else {
                ic.commitText(letter, 1)
            }
            updateModifierButtonStyles()
            return
        }

        val output = if (state.isUppercase()) letter.uppercase() else letter
        ic.commitText(output, 1)
        state.consumeShiftAfterLetter()
        updateShiftButtonStyle()
    }

    private fun handleSymbolTap(symbol: String) {
        currentInputConnection?.commitText(symbol, 1)
    }

    private fun handleEmojiTap(emoji: String) {
        currentInputConnection?.commitText(emoji, 1)
    }

    private fun handleSpace() {
        currentInputConnection?.commitText(" ", 1)
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        val noEnterFlag = editorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) ?: 0

        if (action != null && action != EditorInfo.IME_ACTION_NONE && noEnterFlag == 0) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    // ---------------------------------------------------------------
    // Handlers — Shift (majuscule, mode Lettres)
    // ---------------------------------------------------------------

    private fun handleShiftTap() {
        state.onShiftTapped()
        updateShiftButtonStyle()
    }

    // ---------------------------------------------------------------
    // Handlers — Mode Code : modificateurs et touches spéciales
    // ---------------------------------------------------------------

    private fun handleCtrlTap() {
        state.toggleCtrl()
        updateModifierButtonStyles()
    }

    private fun handleAltTap() {
        state.toggleAlt()
        updateModifierButtonStyles()
    }

    private fun handleShiftMetaTap() {
        state.toggleShiftMeta()
        updateModifierButtonStyles()
    }

    /**
     * Exécute une touche du Mode Code. Si un modificateur (Ctrl/Alt/Shift)
     * est armé, ou si la touche est une touche spéciale (Tab, Esc, flèches),
     * on envoie un vrai KeyEvent avec méta-état pour que les raccourcis
     * fonctionnent dans des applications comme Termux ou Acode. Sinon on
     * insère simplement le caractère comme texte normal.
     */
    private fun performCodeAction(label: String) {
        val ic = currentInputConnection ?: return

        val specialKeyCode = KeyEventMapper.keyCodeForSpecial(label)
        val punctuationKeyCode = if (label.length == 1) {
            KeyEventMapper.keyCodeForPunctuation(label[0])
        } else null

        val keyCode = specialKeyCode ?: punctuationKeyCode

        if (keyCode != null) {
            val meta = state.consumeModifiers()
            KeyEventMapper.dispatch(ic, keyCode, meta)
        } else {
            state.consumeModifiers()
            ic.commitText(label, 1)
        }

        updateModifierButtonStyles()
    }

    // ---------------------------------------------------------------
    // Handlers — navigation entre modes
    // ---------------------------------------------------------------

    private fun switchToEmojiMode() {
        state.mode = KeyboardMode.EMOJI
        currentEmojiCategoryIndex = 0
        render()
    }

    private fun switchToLettersMode() {
        state.mode = KeyboardMode.LETTERS
        render()
    }

    private fun toggleNormalCodeMode() {
        state.toggleNormalCode()
        render()
    }

    private fun lockModeAndNotify() {
        state.lockCurrentModeAsDefault()
        val label = if (state.mode == KeyboardMode.CODE) "Mode Code" else "Mode Normal"
        Toast.makeText(this, "$label verrouillé comme mode par défaut", Toast.LENGTH_SHORT).show()
    }

    private fun cycleLayout() {
        state.cycleLayout()
        render()
    }

    private fun selectEmojiCategory(index: Int) {
        currentEmojiCategoryIndex = index
        render()
    }

    // ---------------------------------------------------------------
    // Style
    // ---------------------------------------------------------------

    private fun updateShiftButtonStyle() {
        val button = shiftButton ?: return
        when (state.shiftState) {
            ShiftState.OFF -> {
                button.text = "⇧"
                button.setBackgroundColor(Color.parseColor("#BBBBBB"))
            }
            ShiftState.SHIFT -> {
                button.text = "⇧"
                button.setBackgroundColor(Color.parseColor("#888888"))
            }
            ShiftState.CAPS_LOCK -> {
                button.text = "⇪"
                button.setBackgroundColor(Color.parseColor("#4CAF50"))
            }
        }
    }

    private fun updateModifierButtonStyles() {
        ctrlButton?.setBackgroundColor(
            Color.parseColor(if (state.ctrlArmed) "#4CAF50" else "#BBBBBB")
        )
        altButton?.setBackgroundColor(
            Color.parseColor(if (state.altArmed) "#4CAF50" else "#BBBBBB")
        )
        shiftMetaButton?.setBackgroundColor(
            Color.parseColor(if (state.shiftMetaArmed) "#4CAF50" else "#BBBBBB")
        )
    }

    private fun layoutAbbreviation(type: KeyboardLayoutType): String = when (type) {
        KeyboardLayoutType.AZERTY -> "AZE"
        KeyboardLayoutType.QWERTY -> "QWE"
        KeyboardLayoutType.QWERTZ -> "QWZ"
    }

    // ---------------------------------------------------------------
    // Helpers de construction de vues
    // ---------------------------------------------------------------

    private fun verticalContainer(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun horizontalRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun makeKeyButton(
        label: String,
        weight: Float,
        heightDp: Int = 48,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit
    ): Button {
        val button = Button(this)
        button.text = label
        button.isAllCaps = false
        val params = LinearLayout.LayoutParams(0, dp(heightDp), weight)
        params.setMargins(dp(2), dp(2), dp(2), dp(2))
        button.layoutParams = params
        button.setOnClickListener { onClick() }
        if (onLongClick != null) {
            button.setOnLongClickListener {
                onLongClick()
                true
            }
        }
        return button
    }

    private fun makeSpacer(weight: Float): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), weight)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}