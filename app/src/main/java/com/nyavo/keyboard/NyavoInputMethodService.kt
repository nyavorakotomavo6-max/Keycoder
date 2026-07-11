package com.nyavo.keyboard

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout

class NyavoInputMethodService : InputMethodService() {

    private lateinit var state: KeyboardState
    private var rootContainer: LinearLayout? = null
    private var shiftButton: Button? = null
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

        val content = when (state.mode) {
            KeyboardMode.LETTERS -> buildLettersView()
            KeyboardMode.EMOJI -> buildEmojiView()
        }
        root.addView(content)

        if (state.mode == KeyboardMode.LETTERS) {
            updateShiftButtonStyle()
        }
    }

    private fun buildLettersView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val rows = KeyboardLayoutData.rowsFor(state.layoutType)

        container.addView(buildLetterRow(rows[0]))
        container.addView(buildLetterRow(rows[1]))
        container.addView(buildThirdLetterRow(rows[2]))
        container.addView(buildPunctuationRow())
        container.addView(buildBottomRow())

        return container
    }

    private fun buildEmojiView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        container.addView(buildEmojiCategoryTabs())
        container.addView(buildEmojiGrid())
        container.addView(buildEmojiBottomRow())

        return container
    }

    // ---------------------------------------------------------------
    // Rangées — mode lettres
    // ---------------------------------------------------------------

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

    private fun buildBottomRow(): View {
        val row = horizontalRow()
        row.addView(makeKeyButton("😊", 1.5f) { switchToEmojiMode() })
        row.addView(makeKeyButton(layoutAbbreviation(state.layoutType), 1.5f) { cycleLayout() })
        row.addView(makeKeyButton("espace", 4f) { handleSpace() })
        row.addView(makeKeyButton("↵", 2f) { handleEnter() })
        return row
    }

    // ---------------------------------------------------------------
    // Rangées — mode emoji
    // ---------------------------------------------------------------

    private fun buildEmojiCategoryTabs(): View {
        val row = horizontalRow()
        EmojiData.CATEGORIES.forEachIndexed { index, category ->
            val label = category.label.take(4)
            row.addView(makeKeyButton(label, 1f, heightDp = 40) { selectEmojiCategory(index) })
        }
        return row
    }

    private fun buildEmojiGrid(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

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
    // Handlers
    // ---------------------------------------------------------------

    private fun handleLetterTap(letter: String) {
        val output = if (state.isUppercase()) letter.uppercase() else letter
        currentInputConnection?.commitText(output, 1)
        state.consumeShiftAfterLetter()
        updateShiftButtonStyle()
    }

    private fun handleSymbolTap(symbol: String) {
        currentInputConnection?.commitText(symbol, 1)
    }

    private fun handleEmojiTap(emoji: String) {
        currentInputConnection?.commitText(emoji, 1)
    }

    private fun handleShiftTap() {
        state.onShiftTapped()
        updateShiftButtonStyle()
    }

    private fun cycleLayout() {
        state.cycleLayout()
        render()
    }

    private fun switchToEmojiMode() {
        state.mode = KeyboardMode.EMOJI
        currentEmojiCategoryIndex = 0
        render()
    }

    private fun switchToLettersMode() {
        state.mode = KeyboardMode.LETTERS
        render()
    }

    private fun selectEmojiCategory(index: Int) {
        currentEmojiCategoryIndex = index
        render()
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

    private fun layoutAbbreviation(type: KeyboardLayoutType): String = when (type) {
        KeyboardLayoutType.AZERTY -> "AZE"
        KeyboardLayoutType.QWERTY -> "QWE"
        KeyboardLayoutType.QWERTZ -> "QWZ"
    }

    // ---------------------------------------------------------------
    // Helpers de construction de vues
    // ---------------------------------------------------------------

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
        onClick: () -> Unit
    ): Button {
        val button = Button(this)
        button.text = label
        button.textAllCaps = false
        val params = LinearLayout.LayoutParams(0, dp(heightDp), weight)
        params.setMargins(dp(2), dp(2), dp(2), dp(2))
        button.layoutParams = params
        button.setOnClickListener { onClick() }
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