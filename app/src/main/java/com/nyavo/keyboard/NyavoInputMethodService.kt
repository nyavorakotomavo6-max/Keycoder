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
import android.view.ViewAnimationUtils
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.hypot

class NyavoInputMethodService : InputMethodService() {

    private lateinit var state: KeyboardState
    private var rootContainer: LinearLayout? = null
    private var glowOverlay: View? = null
    private var shiftButton: Button? = null
    private var currentEmojiCategoryIndex = 0

    private val standardKeyHeightDp = 42
    private val floatAnimators = mutableListOf<ObjectAnimator>()

    private val topRowDigits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    private val normalTextSizeSp = 18f
    private val bigTextSizeSp = 26f

    private val sharedTypeface by lazy { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }

    private data class PopupZone(val view: Button, val value: String)

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var currentPopup: PopupWindow? = null
    private var currentZones: List<PopupZone> = emptyList()
    private var currentZoneWidthPx = 1
    private var currentPopupStartX = 0
    private var currentHighlightIndex = 0
    private val longPressDelayMs = 320L

    private var previewPopup: PopupWindow? = null
    private var previewText: TextView? = null

    private var glowFadeRunnable: Runnable? = null

    private val cursorStepThresholdPx get() = dp(24)

    // Empêche Android de basculer le clavier en mode plein écran, ce qui
    // était la cause principale de la hauteur excessive et du flash
    // lumineux qui semblait s'étendre à tout l'écran (le calque de glow
    // était alors dimensionné sur une fenêtre plein écran plutôt que sur
    // le clavier seul).
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreate() {
        super.onCreate()
        state = KeyboardState(this)
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null) as FrameLayout
        val card = root.findViewById<LinearLayout>(R.id.keyboard_card)
        glowOverlay = root.findViewById(R.id.keyboard_glow_overlay)
        rootContainer = card
        render()
        addFloatingAnimation(card)
        setupPreviewPopup()
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
        dismissPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        floatAnimators.forEach { it.cancel() }
        dismissPopup()
        dismissPreview()
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
    // Flash lumineux — ondulation circulaire partant de la touche tapée,
    // strictement bornée aux limites du clavier (jamais de l'écran).
    // ---------------------------------------------------------------

    private fun triggerGlowFlash(origin: View) {
        val overlay = glowOverlay ?: return
        if (overlay.width == 0 || overlay.height == 0) return

        val originLoc = IntArray(2)
        origin.getLocationOnScreen(originLoc)
        val overlayLoc = IntArray(2)
        overlay.getLocationOnScreen(overlayLoc)

        val cx = (originLoc[0] - overlayLoc[0] + origin.width / 2).coerceIn(0, overlay.width)
        val cy = (originLoc[1] - overlayLoc[1] + origin.height / 2).coerceIn(0, overlay.height)
        val finalRadius = hypot(overlay.width.toFloat(), overlay.height.toFloat())

        glowFadeRunnable?.let { longPressHandler.removeCallbacks(it) }

        overlay.visibility = View.VISIBLE
        val reveal = ViewAnimationUtils.createCircularReveal(overlay, cx, cy, 0f, finalRadius)
        reveal.duration = 220L
        reveal.start()

        val fadeRunnable = Runnable { overlay.visibility = View.INVISIBLE }
        glowFadeRunnable = fadeRunnable
        longPressHandler.postDelayed(fadeRunnable, 220L + 90L)
    }

    // ---------------------------------------------------------------
    // Bulle d'aperçu de touche
    // ---------------------------------------------------------------

    private fun setupPreviewPopup() {
        val bubble = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            textSize = 22f
            typeface = sharedTypeface
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = ContextCompat.getDrawable(this@NyavoInputMethodService, R.drawable.key_bg_shift)
        }
        previewText = bubble
        previewPopup = PopupWindow(
            bubble,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = false
            isTouchable = false
        }
    }

    private fun showPreview(anchor: View, label: String) {
        val popup = previewPopup ?: return
        val bubble = previewText ?: return
        bubble.text = label

        bubble.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val bubbleWidth = bubble.measuredWidth
        val bubbleHeight = bubble.measuredHeight

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val x = location[0] + anchor.width / 2 - bubbleWidth / 2
        val y = location[1] - bubbleHeight - dp(6)

        if (!popup.isShowing) {
            popup.width = bubbleWidth
            popup.height = bubbleHeight
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        } else {
            popup.update(x, y, bubbleWidth, bubbleHeight)
        }
    }

    private fun dismissPreview() {
        previewPopup?.let { if (it.isShowing) it.dismiss() }
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
        row.addView(makeSpaceButton(5f))
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
                makeKeyButton(label, 1f, heightDp = 30, isSpecial = true) { selectEmojiCategory(index) }
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
                    makeKeyButton(emoji, 1f, heightDp = 40, isSpecial = false) { handleEmojiTap(emoji) }
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
        row.addView(makeSpaceButton(5f))
        return row
    }

    // ---------------------------------------------------------------
    // Barre d'espace : tap = espace, appui long + glissé = curseur
    // ---------------------------------------------------------------

    private fun makeSpaceButton(weight: Float): Button {
        val button = Button(this)
        button.text = "espace"
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
        button.typeface = sharedTypeface
        button.setTextColor(ContextCompat.getColor(this, R.color.key_text))
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, normalTextSizeSp)
        button.setBackgroundResource(R.drawable.key_bg_special)
        button.isHapticFeedbackEnabled = true

        val params = LinearLayout.LayoutParams(0, dp(standardKeyHeightDp), weight)
        params.setMargins(dp(2), dp(2), dp(2), dp(2))
        button.layoutParams = params

        attachSpaceGestureBehavior(button)
        return button
    }

    private fun attachSpaceGestureBehavior(button: Button) {
        var cursorModeActive = false
        var lastStepX = 0f
        var lastStepY = 0f

        val longPressRunnable = Runnable {
            cursorModeActive = true
            button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            button.setBackgroundResource(R.drawable.key_bg_shift)
        }

        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    cursorModeActive = false
                    lastStepX = event.rawX
                    lastStepY = event.rawY
                    view.animate().translationY(2f).setDuration(30L).start()
                    longPressHandler.postDelayed(longPressRunnable, longPressDelayMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (cursorModeActive) {
                        processCursorDrag(event.rawX, event.rawY, lastStepX, lastStepY) { newX, newY ->
                            lastStepX = newX
                            lastStepY = newY
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    view.animate().translationY(0f).setDuration(50L).start()
                    if (cursorModeActive) {
                        button.setBackgroundResource(R.drawable.key_bg_special)
                    } else {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        triggerGlowFlash(view)
                        handleSpace()
                    }
                    cursorModeActive = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    view.animate().translationY(0f).setDuration(50L).start()
                    button.setBackgroundResource(R.drawable.key_bg_special)
                    cursorModeActive = false
                    true
                }
                else -> false
            }
        }
    }

    private fun processCursorDrag(
        currentX: Float,
        currentY: Float,
        lastStepX: Float,
        lastStepY: Float,
        onStepConsumed: (Float, Float) -> Unit
    ) {
        val dx = currentX - lastStepX
        val dy = currentY - lastStepY

        if (abs(dx) >= abs(dy)) {
            if (dx >= cursorStepThresholdPx) {
                moveCursor(KeyEvent.KEYCODE_DPAD_RIGHT)
                onStepConsumed(lastStepX + cursorStepThresholdPx, currentY)
            } else if (dx <= -cursorStepThresholdPx) {
                moveCursor(KeyEvent.KEYCODE_DPAD_LEFT)
                onStepConsumed(lastStepX - cursorStepThresholdPx, currentY)
            }
        } else {
            if (dy >= cursorStepThresholdPx) {
                moveCursor(KeyEvent.KEYCODE_DPAD_DOWN)
                onStepConsumed(currentX, lastStepY + cursorStepThresholdPx)
            } else if (dy <= -cursorStepThresholdPx) {
                moveCursor(KeyEvent.KEYCODE_DPAD_UP)
                onStepConsumed(currentX, lastStepY - cursorStepThresholdPx)
            }
        }
    }

    private fun moveCursor(keyCode: Int) {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    // ---------------------------------------------------------------
    // Touches lettres — insertion instantanée au contact (ACTION_DOWN)
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
        button.typeface = sharedTypeface
        button.setTextColor(ContextCompat.getColor(this, R.color.key_text))
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, normalTextSizeSp)
        button.setBackgroundResource(R.drawable.key_bg_normal)
        button.isHapticFeedbackEnabled = true

        val params = LinearLayout.LayoutParams(0, dp(standardKeyHeightDp), 1f)
        params.setMargins(dp(2), dp(2), dp(2), dp(2))
        button.layoutParams = params

        attachLetterKeyBehavior(button, letter, topRowIndex)
        return button
    }

    private fun attachLetterKeyBehavior(button: Button, letter: String, topRowIndex: Int?) {
        var longPressTriggered = false

        val longPressRunnable = Runnable {
            longPressTriggered = true
            dismissPreview()
            currentInputConnection?.deleteSurroundingText(1, 0)
            button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showSymbolPopup(button, letter, topRowIndex)
        }

        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressTriggered = false
                    view.animate().translationY(2f).setDuration(30L).start()
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    triggerGlowFlash(view)
                    showPreview(view, letter.uppercase())
                    handleLetterTap(letter)
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
                    view.animate().translationY(0f).setDuration(50L).start()
                    dismissPreview()
                    if (longPressTriggered) {
                        commitHighlightedZone()
                    }
                    longPressTriggered = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    view.animate().translationY(0f).setDuration(50L).start()
                    dismissPreview()
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
                typeface = sharedTypeface
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
            val zone = currentZones[currentHighlightIndex]
            currentInputConnection?.commitText(zone.value, 1)
            triggerGlowFlash(zone.view)
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
        button.typeface = sharedTypeface
        button.setTextColor(ContextCompat.getColor(this, R.color.key_text))
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        button.setBackgroundResource(
            if (isSpecial) R.drawable.key_bg_special else R.drawable.key_bg_normal
        )
        button.isHapticFeedbackEnabled = true

        val params = LinearLayout.LayoutParams(0, dp(heightDp), weight)
        params.setMargins(dp(2), dp(2), dp(2), dp(2))
        button.layoutParams = params
        button.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            triggerGlowFlash(it)
            onClick()
        }
        attachSinkAnimation(button)
        return button
    }

    private fun attachSinkAnimation(button: Button) {
        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().translationY(2f).setDuration(30L).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().translationY(0f).setDuration(50L).start()
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