package com.nyavo.keyboard

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat

class NyavoInputMethodService : InputMethodService() {

    private lateinit var state: KeyboardState
    private var rootContainer: LinearLayout? = null
    private var shiftButton: Button? = null
    private var currentEmojiCategoryIndex = 0

    private val standardKeyHeightDp = 50
    private val floatAnimators = mutableListOf<ObjectAnimator>()

    private val topRowDigits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    // Tailles de texte : agrandies pour les touches à fort impact
    // ergonomique (Entrée, Suppr, Shift, flèches) comme demandé, tout en
    // gardant les lettres normales lisibles sans déséquilibrer le clavier.
    private val normalTextSizeSp = 18f
    private val bigTextSizeSp = 26f

    // --- Popup symbole/chiffre à appui long ---
    private data class PopupZone(val view: Button, val value: String)

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var currentPopup: PopupWindow? = null
    private var currentZones: List<PopupZone> = emptyList()
    private var currentZoneWidthPx = 1
    private var currentPopupStartX = 0
    private var currentHighlightIndex = 0

    private val longPressDelayMs = 320L

    override fun onCreate() {
        super.onCreate()
        state = KeyboardState(this)
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null) as FrameLayout
        val card = root.findViewById<LinearLayout>(R.id.keyboard_card)
        rootContainer = card
        render()

        val arrowCluster = buildArrowCluster()
        val arrowParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(2)
            rightMargin = dp(2)
        }
        root.addView(arrowCluster, arrowParams)

        addFloatingAnimation(card)
        addFloatingAnimation(arrowCluster, duration = 2100L)

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        floatAnimators.forEach { if (!it.isRunning) it.start() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        floatAnimators.forEach { it.cancel() }
        dismissPopup()
    }

    override fun onDestroy() {
        super.onDestroy()
        floatAnimators.forEach { it.cancel() }
        dismissPopup()
    }

    // ---------------------------------------------------------------
    // Lévitation
    // ---------------------------------------------------------------

    private fun addFloatingAnimation(target: View, duration: Long = 1800L) {
        val anim = ObjectAnimator.ofFloat(target, "translationY", 0f, -5f, 0f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        floatAnimators.add(anim)
        anim.start()
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

        container.addView(buildLetterRow(rows[0], isTopRow = true))
        container.addView(buildLetterRow(rows[1]))
        container.addView(buildThirdLetterRow(rows[2]))
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

    private fun buildLetterRow(letters: List<String>, isTopRow: Boolean = false): View {
        val row = horizontalRow()
        letters.forEachIndexed { index, letter ->
            row.addView(makeLetterButton(letter, if (isTopRow) index else null))
        }
        return row
    }

    private fun buildThirdLetterRow(letters: List<String>): View {
        val row = horizontalRow()

        val shift = makeKeyButton(
            "⇧", 1.6f, isSpecial = true, textSizeSp = bigTextSizeSp
        ) { handleShiftTap() }
        shiftButton = shift
        row.addView(shift)

        for (letter in letters) {
            row.addView(makeLetterButton(letter, null))
        }

        row.addView(
            makeKeyButton("⌫", 1.6f, isSpecial = true, textSizeSp = bigTextSizeSp) { handleBackspace() }
        )
        return row
    }

    private fun buildBottomRow(): View {
        val row = horizontalRow()
        row.addView(makeKeyButton("😊", 1.2f, isSpecial = true) { switchToEmojiMode() })
        row.addView(
            makeKeyButton(layoutAbbreviation(state.layoutType), 1.2f, isSpecial = true) { cycleLayout() }
        )
        row.addView(makeKeyButton("espace", 5f, isSpecial = true) { handleSpace() })
        row.addView(
            makeKeyButton("↵", 1.8f, isSpecial = true, textSizeSp = bigTextSizeSp) { handleEnter() }
        )
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
                    makeKeyButton(emoji, 1f, heightDp = 48, isSpecial = false) { handleEmojiTap(emoji) }
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
        row.addView(
            makeKeyButton("⌫", 1.6f, isSpecial = true, textSizeSp = bigTextSizeSp) { handleBackspace() }
        )
        row.addView(makeKeyButton("espace", 5f, isSpecial = true) { handleSpace() })
        return row
    }

    // ---------------------------------------------------------------
    // Flèches directionnelles — cluster mobile, sans fond
    // ---------------------------------------------------------------

    private fun buildArrowCluster(): LinearLayout {
        val cluster = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        val handle = TextView(this).apply {
            text = "⠿"
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            textSize = 14f
            gravity = Gravity.CENTER
            alpha = 0.6f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(20)
            )
        }
        attachDragBehavior(handle, cluster)
        cluster.addView(handle)

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(makeArrowSpacer())
        topRow.addView(makeArrowButton("↑", KeyEvent.KEYCODE_DPAD_UP))
        topRow.addView(makeArrowSpacer())

        val bottomRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottomRow.addView(makeArrowButton("←", KeyEvent.KEYCODE_DPAD_LEFT))
        bottomRow.addView(makeArrowButton("↓", KeyEvent.KEYCODE_DPAD_DOWN))
        bottomRow.addView(makeArrowButton("→", KeyEvent.KEYCODE_DPAD_RIGHT))

        cluster.addView(topRow)
        cluster.addView(bottomRow)
        return cluster
    }

    /**
     * Permet de faire glisser tout le cluster de flèches via sa poignée
     * dédiée (⠿). On isole le glissé sur cette poignée, plutôt que sur le
     * cluster entier, pour ne pas interférer avec le tap normal sur
     * chaque flèche.
     */
    private fun attachDragBehavior(handle: View, target: View) {
        var startRawX = 0f
        var startRawY = 0f
        var startTranslationX = 0f
        var startTranslationY = 0f

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startTranslationX = target.translationX
                    startTranslationY = target.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    target.translationX = startTranslationX + dx
                    target.translationY = startTranslationY + dy
                    true
                }
                else -> true
            }
        }
    }

    private fun makeArrowButton(label: String, keyCode: Int): Button {
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
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, bigTextSizeSp)
        // Pas de fond : uniquement le symbole flottant, comme demandé.
        button.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        button.isHapticFeedbackEnabled = true
        button.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        button.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            sendArrowKeyEvent(keyCode)
        }
        attachSinkAnimation(button)
        return button
    }

    private fun makeArrowSpacer(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
        }
    }

    private fun sendArrowKeyEvent(keyCode: Int) {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    // ---------------------------------------------------------------
    // Touches lettres — gestion tactile complète (tap / appui long+glisse)
    // ---------------------------------------------------------------

    private fun makeLetterButton(letter: String, topRowIndex: Int?): Button {
        val button = Button(this)
        button.text = letter.uppercase()
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
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, normalTextSizeSp)
        button.setBackgroundResource(R.drawable.key_bg_normal)
        button.isHapticFeedbackEnabled = true

        val params = LinearLayout.LayoutParams(0, dp(standardKeyHeightDp), 1f)
        params.setMargins(dp(3), dp(3), dp(3), dp(3))
        button.layoutParams = params

        attachLetterKeyBehavior(button, letter, topRowIndex)
        return button
    }

    private fun attachLetterKeyBehavior(button: Button, letter: String, topRowIndex: Int?) {
        var longPressTriggered = false
        val longPressRunnable = Runnable {
            longPressTriggered = true
            button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showSymbolPopup(button, letter, topRowIndex)
        }

        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressTriggered = false
                    view.animate().translationY(2f).setDuration(40L).start()
                    longPressHandler.postDelayed(longPressRunnable, longPressDelayMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longPressTriggered) {
                        updateHoverZone(event.rawX)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    view.animate().translationY(0f).setDuration(60L).start()
                    if (longPressTriggered) {
                        commitHighlightedZone()
                    } else {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        handleLetterTap(letter)
                    }
                    longPressTriggered = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    view.animate().translationY(0f).setDuration(60L).start()
                    if (longPressTriggered) {
                        dismissPopup()
                    }
                    longPressTriggered = false
                    true
                }
                else -> false
            }
        }
    }

    // ---------------------------------------------------------------
    // Popup symbole / chiffre
    // ---------------------------------------------------------------

    private fun showSymbolPopup(anchor: Button, letter: String, topRowIndex: Int?) {
        dismissPopup()

        val symbol = LongPressSymbols.symbolFor(letter) ?: return
        val zoneValues = mutableListOf(symbol)
        if (topRowIndex != null) {
            zoneValues.add(topRowDigits[topRowIndex])
        }

        val popupContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ContextCompat.getDrawable(this@NyavoInputMethodService, R.drawable.keyboard_card_bg)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val zoneButtons = mutableListOf<PopupZone>()
        zoneValues.forEachIndexed { index, value ->
            val zoneBtn = Button(this).apply {
                text = value
                isAllCaps = false
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                minWidth = 0
                minHeight = 0
                minimumWidth = 0
                minimumHeight = 0
                includeFontPadding = false
                stateListAnimator = null
                elevation = 0f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
                setBackgroundResource(if (index == 0) R.drawable.key_bg_shift else R.drawable.key_bg_special)
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            }
            popupContainer.addView(zoneBtn)
            zoneButtons.add(PopupZone(zoneBtn, value))
        }

        popupContainer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val measuredWidth = popupContainer.measuredWidth
        val measuredHeight = popupContainer.measuredHeight

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorCenterX = location[0] + anchor.width / 2
        val popupX = anchorCenterX - measuredWidth / 2
        val popupY = location[1] - measuredHeight - dp(8)

        val popup = PopupWindow(
            popupContainer,
            measuredWidth,
            measuredHeight,
            false
        ).apply {
            isOutsideTouchable = false
            isTouchable = false
        }
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY)

        currentPopup = popup
        currentZones = zoneButtons
        currentZoneWidthPx = if (zoneValues.isNotEmpty()) measuredWidth / zoneValues.size else measuredWidth
        currentPopupStartX = popupX
        currentHighlightIndex = 0
    }

    private fun updateHoverZone(rawX: Float) {
        if (currentZones.isEmpty() || currentZoneWidthPx <= 0) return
        val relative = rawX - currentPopupStartX
        var idx = (relative / currentZoneWidthPx).toInt()
        if (idx < 0) idx = 0
        if (idx > currentZones.size - 1) idx = currentZones.size - 1

        if (idx != currentHighlightIndex) {
            currentHighlightIndex = idx
            currentZones.forEachIndexed { i, zone ->
                zone.view.setBackgroundResource(
                    if (i == currentHighlightIndex) R.drawable.key_bg_shift else R.drawable.key_bg_special
                )
            }
        }
    }

    private fun commitHighlightedZone() {
        if (currentZones.isNotEmpty()) {
            val value = currentZones[currentHighlightIndex].value
            currentInputConnection?.commitText(value, 1)
        }
        dismissPopup()
    }

    private fun dismissPopup() {
        currentPopup?.dismiss()
        currentPopup = null
        currentZones = emptyList()
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
        textSizeSp: Float = normalTextSizeSp,
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
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        button.setBackgroundResource(
            if (isSpecial) R.drawable.key_bg_special else R.drawable.key_bg_normal
        )
        button.isHapticFeedbackEnabled = true

        val params = LinearLayout.LayoutParams(0, dp(heightDp), weight)
        params.setMargins(dp(3), dp(3), dp(3), dp(3))
        button.layoutParams = params
        button.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
        attachSinkAnimation(button)
        return button
    }

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
}