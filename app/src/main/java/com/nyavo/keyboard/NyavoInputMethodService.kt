package com.nyavo.keyboard

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

class NyavoInputMethodService : InputMethodService() {

    private lateinit var state: KeyboardState
    private var rootContainer: LinearLayout? = null
    private var shiftButton: Button? = null
    private var currentEmojiCategoryIndex = 0
    private var floatAnimator: ObjectAnimator? = null

    // Hauteur standard d'une touche, calquée sur les proportions Gboard
    // (~42-44dp) plutôt que sur la version précédente (58dp), qui faisait
    // occuper au clavier près de la moitié de l'écran.
    private val standardKeyHeightDp = 42

    override fun onCreate() {
        super.onCreate()
        state = KeyboardState(this)
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null) as FrameLayout
        val card = root.findViewById<LinearLayout>(R.id.keyboard_card)
        rootContainer = card
        render()
        startFloatingAnimation(card)
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        floatAnimator?.let { if (!it.isRunning) it.start() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        floatAnimator?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        floatAnimator?.cancel()
    }

    // ---------------------------------------------------------------
    // Lévitation douce de l'ensemble du clavier
    // ---------------------------------------------------------------

    private fun startFloatingAnimation(target: View) {
        floatAnimator?.cancel()
        floatAnimator = ObjectAnimator.ofFloat(target, "translationY", 0f, -5f, 0f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        floatAnimator?.start()
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
        val container = verticalContainer()
        val rows = KeyboardLayoutData.rowsFor(state.layoutType)

        container.addView(buildLetterRow(rows[0]))
        container.addView(buildLetterRow(rows[1]))
        container.addView(buildThirdLetterRow(rows[2]))
        container.addView(buildPunctuationRow())
        container.addView(buildBottomRow())

        return container
    }

    private fun buildEmojiView(): View {
        val container = verticalContainer()
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
            row.addView(
                makeKeyButton(
                    label = letter.uppercase(),
                    weight = 1f,
                    isSpecial = false,
                    onLongClick = { handleLongPressSymbol(letter) }
                ) { handleLetterTap(letter) }
            )
        }
        return row
    }

    private fun buildThirdLetterRow(letters: List<String>): View {
        val row = horizontalRow()

        val shift = makeKeyButton("⇧", 1.6f, isSpecial = true) { handleShiftTap() }
        shiftButton = shift
        row.addView(shift)

        for (letter in letters) {
            row.addView(
                makeKeyButton(
                    label = letter.uppercase(),
                    weight = 1f,
                    isSpecial = false,
                    onLongClick = { handleLongPressSymbol(letter) }
                ) { handleLetterTap(letter) }
            )
        }

        row.addView(makeKeyButton("⌫", 1.6f, isSpecial = true) { handleBackspace() })
        return row
    }

    private fun buildPunctuationRow(): View {
        val row = horizontalRow()
        for (symbol in KeyboardLayoutData.QUICK_PUNCTUATION) {
            row.addView(makeKeyButton(symbol, 1f, isSpecial = false) { handleSymbolTap(symbol) })
        }
        return row
    }

    private fun buildBottomRow(): View {
        val row = horizontalRow()
        row.addView(makeKeyButton("😊", 1.2f, isSpecial = true) { switchToEmojiMode() })
        row.addView(
            makeKeyButton(layoutAbbreviation(state.layoutType), 1.2f, isSpecial = true) { cycleLayout() }
        )
        row.addView(makeKeyButton("espace", 5f, isSpecial = true) { handleSpace() })
        row.addView(makeKeyButton("↵", 1.8f, isSpecial = true) { handleEnter() })
        return row
    }

    // ---------------------------------------------------------------
    // Rangées — mode emoji
    // ---------------------------------------------------------------

    private fun buildEmojiCategoryTabs(): View {
        val row = horizontalRow()
        EmojiData.CATEGORIES.forEachIndexed { index, category ->
            val label = category.label.take(4)
            row.addView(
                makeKeyButton(label, 1f, heightDp = 34, isSpecial = true) { selectEmojiCategory(index) }
            )
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
                row.addView(
                    makeKeyButton(emoji, 1f, heightDp = 42, isSpecial = false) { handleEmojiTap(emoji) }
                )
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
        row.addView(makeKeyButton("ABC", 1.6f, isSpecial = true) { switchToLettersMode() })
        row.addView(makeKeyButton("⌫", 1.6f, isSpecial = true) { handleBackspace() })
        row.addView(makeKeyButton("espace", 5f, isSpecial = true) { handleSpace() })
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

    private fun handleLongPressSymbol(letter: String) {
        val symbol = LongPressSymbols.symbolFor(letter) ?: return
        currentInputConnection?.commitText(symbol, 1)
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
                button.setBackgroundResource(R.drawable.key_bg_special)
            }
            ShiftState.SHIFT -> {
                button.text = "⇧"
                button.setBackgroundResource(R.drawable.key_bg_shift)
            }
            ShiftState.CAPS_LOCK -> {
                button.text = "⇪"
                button.setBackgroundResource(R.drawable.key_bg_capslock)
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
        heightDp: Int = standardKeyHeightDp,
        isSpecial: Boolean = false,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit
    ): Button {
        val button = Button(this)
        button.text = label
        button.isAllCaps = false
        button.gravity = Gravity.CENTER
        button.setPadding(0, 0, 0, 0)
        button.minWidth = 0
        button.minHeight = 0
        button.minimumWidth = 0
        button.minimumHeight = 0
        button.includeFontPadding = false
        button.stateListAnimator = null
        button.elevation = 0f
        button.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        button.setTextColor(ContextCompat.getColor(this, R.color.key_text))
        button.setBackgroundResource(
            if (isSpecial) R.drawable.key_bg_special else R.drawable.key_bg_normal
        )

        // Marges resserrées (5dp -> 2.5dp) pour rapprocher l'espacement
        // vertical/horizontal des proportions Gboard, sans coller les
        // touches entre elles au point de nuire à la précision de frappe.
        val params = LinearLayout.LayoutParams(0, dp(heightDp), weight)
        params.setMargins(dpF(2.5f), dpF(2.5f), dpF(2.5f), dpF(2.5f))
        button.layoutParams = params
        button.setOnClickListener { onClick() }
        if (onLongClick != null) {
            button.setOnLongClickListener {
                onLongClick()
                true
            }
        }
        attachSinkAnimation(button)
        return button
    }

    /**
     * Effet "enfoncement d'un pixel" : léger déplacement vertical au
     * contact du doigt, retour immédiat au relâchement. Le changement de
     * couleur (ombre/lumière) est géré automatiquement par le selector
     * XML via l'état natif Android state_pressed. Transformation GPU
     * pure, aucun recalcul de layout : coût négligeable, tient 60 FPS.
     */
    private fun attachSinkAnimation(button: Button) {
        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().translationY(2f).setDuration(40L).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().translationY(0f).setDuration(60L).start()
                }
            }
            false
        }
    }

    private fun makeSpacer(weight: Float): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(standardKeyHeightDp), weight)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun dpF(value: Float): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}