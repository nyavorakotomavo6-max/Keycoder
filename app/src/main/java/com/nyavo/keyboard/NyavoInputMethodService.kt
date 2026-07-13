package com.nyavo.keyboard

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.abs
import kotlin.math.hypot

class NyavoInputMethodService : InputMethodService() {

    private lateinit var state: KeyboardState
    private var rootContainer: LinearLayout? = null
    private var glowOverlay: View? = null
    private var freezeOverlay: View? = null
    private var shiftButton: Button? = null
    private var currentEmojiCategoryIndex = 0

    private val standardKeyHeightDp = 42
    private val floatAnimators = mutableListOf<ObjectAnimator>()
    private var floatBaseDuration = 1800L

    private val topRowDigits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    private val normalTextSizeSp = 18f
    private val bigTextSizeSp = 26f
    private val cornerTextSizeSp = 9f

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
    private var lastGlowTimeMs = 0L
    private var lastKeystrokeTimeMs = 0L

    private val cursorStepThresholdPx get() = dp(24)

    // ========== VAULT MODE ==========
    private val vaultPrefs by lazy { getSharedPreferences("nyavo_vault", MODE_PRIVATE) }
    private val vaultKeyAlias = "nyavo_vault_aes_key"
    private var vaultPopup: PopupWindow? = null
    private var addCredentialPopup: PopupWindow? = null
    private val vaultLongPressMs = 3000L
    // =================================

    // ========== GAMIFICATION ==========
    private val gameState = GameState()
    private var comboLabel: TextView? = null
    private var bossPopup: PopupWindow? = null
    private var bossProgressLabel: TextView? = null
    private var isKeyboardFrozen = false
    // ===================================

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreate() {
        super.onCreate()
        state = KeyboardState(this)
    }

    override fun onCreateInputView(): View {
        floatAnimators.forEach { it.cancel() }
        floatAnimators.clear()

        val root = layoutInflater.inflate(R.layout.keyboard_view, null) as FrameLayout

        val card = root.findViewById<LinearLayout>(R.id.keyboard_card)
        glowOverlay = root.findViewById(R.id.keyboard_glow_overlay)
        freezeOverlay = root.findViewById(R.id.keyboard_freeze_overlay)
        rootContainer = card
        render()
        addFloatingAnimation(card)
        setupPreviewPopup()
        syncOverlaySizesToCard(card)
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
        dismissVaultPopup()
        dismissAddCredentialPopup()
        dismissBossPopup()
        longPressHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatAnimators.forEach { it.cancel() }
        floatAnimators.clear()
        dismissPopup()
        dismissPreview()
        dismissVaultPopup()
        dismissAddCredentialPopup()
        dismissBossPopup()
        longPressHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Cale la taille des calques (lueur, gel) exactement sur celle de la
     * carte du clavier, par code plutôt qu'en XML match_parent, pour
     * éviter que la fenêtre entière du clavier ne s'étende à tout
     * l'écran (ce qui poussait la barre d'envoi des autres apps).
     */
    private fun syncOverlaySizesToCard(card: View) {
        card.viewTreeObserver.addOnGlobalLayoutListener {
            val glow = glowOverlay
            val freeze = freezeOverlay
            if (glow != null) {
                val p = glow.layoutParams
                if (p.width != card.width || p.height != card.height) {
                    p.width = card.width; p.height = card.height; glow.layoutParams = p
                }
            }
            if (freeze != null) {
                val p = freeze.layoutParams
                if (p.width != card.width || p.height != card.height) {
                    p.width = card.width; p.height = card.height; freeze.layoutParams = p
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Lévitation & Effets Visuels
    // ---------------------------------------------------------------

    private fun addFloatingAnimation(target: View, duration: Long = floatBaseDuration) {
        val anim = ObjectAnimator.ofFloat(target, "translationY", 0f, -5f, 0f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        floatAnimators.add(anim)
        anim.start()
    }

    private fun rebuildFloatAnimation(card: View, duration: Long) {
        floatBaseDuration = duration
        floatAnimators.forEach { it.cancel() }
        floatAnimators.clear()
        addFloatingAnimation(card, duration)
    }

    private fun triggerGlowFlash(origin: View) {
        val overlay = glowOverlay ?: return
        if (overlay.width == 0 || overlay.height == 0 || !overlay.isAttachedToWindow) return

        // Anti-saturation : on ignore les flashs trop rapprochés pour ne
        // pas empiler les animateurs pendant une frappe très rapide, ce
        // qui pourrait provoquer du jank perçu comme des lettres qui ne
        // s'affichent pas.
        val now = System.currentTimeMillis()
        if (now - lastGlowTimeMs < 60L) return
        lastGlowTimeMs = now

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
        reveal.duration = 200L
        reveal.start()

        val fadeRunnable = Runnable { overlay.visibility = View.INVISIBLE }
        glowFadeRunnable = fadeRunnable
        longPressHandler.postDelayed(fadeRunnable, 200L + 80L)
    }

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
        if (!anchor.isAttachedToWindow) return
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

    /**
     * Positionne un popup (fenêtre flottante) au-dessus de l'ensemble du
     * clavier, centré horizontalement — utilisé par Vault, l'ajout de
     * secret, et le boss fight, pour éviter qu'ils ne se superposent au
     * clavier lui-même.
     *
     * CORRECTION : utilisation de showAsDropDown avec offset négatif en Y
     * pour un positionnement fiable par rapport à la carte du clavier,
     * sans décalage dû aux coordonnées fenêtre/écran.
     */
    private fun showPopupAboveKeyboard(popup: PopupWindow, content: View, widthDp: Int) {
        val root = rootContainer ?: return
        content.measure(
            View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val measuredWidth = content.measuredWidth
        val measuredHeight = content.measuredHeight

        popup.width = measuredWidth
        popup.height = measuredHeight

        val xoff = (root.width - measuredWidth) / 2
        val yoff = -measuredHeight - dp(8)
        popup.showAsDropDown(root, xoff, yoff)
    }

    // ---------------------------------------------------------------
    // Rendu Global
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
        updateComboLabelStyle()
    }

    private fun buildLettersView(): View {
        val container = verticalContainer()
        val rows = KeyboardLayoutData.rowsFor(state.layoutType)

        container.addView(buildComboBar())
        container.addView(buildDevRow())

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
    // Barre de combo (gamification)
    // ---------------------------------------------------------------

    private fun buildComboBar(): View {
        val label = TextView(this).apply {
            text = "COMBO x0"
            typeface = sharedTypeface
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.combo_base))
            setPadding(0, dp(2), 0, dp(2))
        }
        comboLabel = label
        return label
    }

    private fun updateComboLabelStyle() {
        val label = comboLabel ?: return
        label.text = "COMBO x${gameState.comboCount}"
        val colorRes = when (gameState.tier()) {
            ComboTier.BASE -> R.color.combo_base
            ComboTier.GOLD -> R.color.combo_gold
            ComboTier.FIRE -> R.color.combo_fire
            ComboTier.NEON -> R.color.combo_neon
        }
        label.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun onSuccessfulKeystroke() {
        val now = System.currentTimeMillis()
        gameState.registerKeystroke(now)
        updateComboLabelStyle()

        val card = rootContainer
        when (gameState.tier()) {
            ComboTier.NEON -> {
                if (card != null && floatBaseDuration != 900L) rebuildFloatAnimation(card, 900L)
                vibrateStrong()
            }
            ComboTier.FIRE -> {
                if (card != null && floatBaseDuration != 1300L) rebuildFloatAnimation(card, 1300L)
            }
            else -> {
                if (card != null && floatBaseDuration != 1800L) rebuildFloatAnimation(card, 1800L)
            }
        }

        if (gameState.shouldTriggerBoss()) {
            startBossFight()
        }
    }

    private fun breakComboWithFeedback() {
        gameState.breakCombo()
        updateComboLabelStyle()
        val card = rootContainer
        if (card != null && floatBaseDuration != 1800L) rebuildFloatAnimation(card, 1800L)
        vibrateStrong()
        shakeCard()
    }

    private fun vibrateStrong() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun shakeCard() {
        val card = rootContainer ?: return
        card.animate().cancel()
        val amplitude = dp(6).toFloat()
        card.animate()
            .translationX(amplitude)
            .setDuration(40L)
            .withEndAction {
                card.animate().translationX(-amplitude).setDuration(40L).withEndAction {
                    card.animate().translationX(amplitude / 2).setDuration(40L).withEndAction {
                        card.animate().translationX(0f).setDuration(40L).start()
                    }.start()
                }.start()
            }
            .start()
    }

    // ---------------------------------------------------------------
    // Boss Fight
    // ---------------------------------------------------------------

    private fun startBossFight() {
        val word = gameState.startBoss()
        freezeKeyboard(false)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = ContextCompat.getDrawable(this@NyavoInputMethodService, R.drawable.keyboard_card_bg)
        }
        val title = TextView(this).apply {
            text = "⚔ BOSS : tape ce mot en 5s"
            textSize = 14f
            typeface = sharedTypeface
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.combo_fire))
            gravity = Gravity.CENTER
        }
        val wordLabel = TextView(this).apply {
            text = word
            textSize = 20f
            typeface = sharedTypeface
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            letterSpacing = 0.15f
        }
        bossProgressLabel = wordLabel
        container.addView(title)
        container.addView(wordLabel)

        val popup = PopupWindow(container, dp(280), LinearLayout.LayoutParams.WRAP_CONTENT, false).apply {
            isOutsideTouchable = false
            isTouchable = false
            setBackgroundDrawable(null)
        }
        showPopupAboveKeyboard(popup, container, 280)
        bossPopup = popup

        longPressHandler.postDelayed({
            if (gameState.bossActive) {
                onBossFail()
            }
        }, 5000L)
    }

    private fun updateBossProgressDisplay() {
        val label = bossProgressLabel ?: return
        val word = gameState.bossWord
        val progress = gameState.bossProgress
        val done = word.take(progress)
        val remaining = word.drop(progress)
        label.text = "$done$remaining"
    }

    private fun onBossSuccess() {
        gameState.endBoss()
        dismissBossPopup()
        gameState.addComboBonus(50)
        updateComboLabelStyle()
        rootContainer?.let { triggerGlowFlash(it) }
    }

    private fun onBossFail() {
        gameState.endBoss()
        dismissBossPopup()
        breakComboWithFeedback()
        freezeKeyboard(true)
        longPressHandler.postDelayed({ freezeKeyboard(false) }, 2000L)
    }

    private fun dismissBossPopup() {
        bossPopup?.dismiss()
        bossPopup = null
        bossProgressLabel = null
    }

    private fun freezeKeyboard(frozen: Boolean) {
        isKeyboardFrozen = frozen
        freezeOverlay?.apply {
            visibility = if (frozen) View.VISIBLE else View.GONE
            isClickable = frozen
        }
    }

    // ---------------------------------------------------------------
    // Barre d'outils Développeur
    // ---------------------------------------------------------------

    private fun buildDevRow(): View {
        val row = horizontalRow()

        row.addView(makeKeyButton("✂️", 0.9f, heightDp = 34, isSpecial = true, hapticFeedback = -1) {
            handleEditAction(android.R.id.cut)
        })
        row.addView(makeKeyButton("📋", 0.9f, heightDp = 34, isSpecial = true, hapticFeedback = -1) {
            handleEditAction(android.R.id.paste)
        })

        val devSymbols = listOf("{", "}", "[", "]", "(", ")", ";", "=")
        for (symbol in devSymbols) {
            row.addView(makeKeyButton(symbol, 1.0f, heightDp = 34, isSpecial = false, textSizeSp = 16f, hapticFeedback = -1) {
                handleDevSymbolTap(symbol)
            })
        }

        row.addView(makeKeyButton("ALL", 1.1f, heightDp = 34, isSpecial = true, textSizeSp = 11f, hapticFeedback = -1) {
            handleEditAction(android.R.id.selectAll)
        })

        if (isPasswordField()) {
            row.addView(makeKeyButton("🔐", 1.0f, heightDp = 34, isSpecial = true, hapticFeedback = HapticFeedbackConstants.CONFIRM) {
                showVaultPopup()
            })
        }

        return row
    }

    private fun handleDevSymbolTap(symbol: String) {
        val ic = currentInputConnection ?: return
        when (symbol) {
            "{" -> { ic.commitText("{}", 1); moveCursor(KeyEvent.KEYCODE_DPAD_LEFT) }
            "[" -> { ic.commitText("[]", 1); moveCursor(KeyEvent.KEYCODE_DPAD_LEFT) }
            "(" -> { ic.commitText("()", 1); moveCursor(KeyEvent.KEYCODE_DPAD_LEFT) }
            else -> ic.commitText(symbol, 1)
        }
    }

    private fun handleEditAction(actionId: Int) {
        currentInputConnection?.performContextMenuAction(actionId)
    }

    // ---------------------------------------------------------------
    // Rangées Standard
    // ---------------------------------------------------------------

    private fun buildLetterRow(letters: List<String>, isTopRow: Boolean = false): View {
        val row = horizontalRow()
        letters.forEachIndexed { index, letter ->
            row.addView(makeLetterKey(letter, if (isTopRow) index else null))
        }
        return row
    }

    private fun buildThirdLetterRow(letters: List<String>): View {
        val row = horizontalRow()

        val shift = Button(this).apply {
            text = "⇧"
            isAllCaps = false
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minWidth = 0; minHeight = 0; minimumWidth = 0; minimumHeight = 0
            includeFontPadding = false
            stateListAnimator = null
            elevation = 0f
            typeface = sharedTypeface
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, bigTextSizeSp)
            setBackgroundResource(R.drawable.key_bg_special)
            isHapticFeedbackEnabled = true
            layoutParams = LinearLayout.LayoutParams(0, dp(standardKeyHeightDp), 1.6f).apply {
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
        }
        shiftButton = shift
        attachShiftBehavior(shift)
        row.addView(shift)

        for (letter in letters) {
            row.addView(makeLetterKey(letter, null))
        }

        row.addView(makeKeyButton("⌫", 1.6f, isSpecial = true, textSizeSp = bigTextSizeSp, hapticFeedback = HapticFeedbackConstants.REJECT) { handleBackspace() })
        return row
    }

    private fun buildBottomRow(): View {
        val row = horizontalRow()
        row.addView(makeKeyButton("😊", 1.2f, isSpecial = true) { switchToEmojiMode() })
        row.addView(makeKeyButton(layoutAbbreviation(state.layoutType), 1.2f, isSpecial = true) { cycleLayout() })
        row.addView(makeSpaceButton(5f))
        row.addView(makeKeyButton("↵", 1.8f, isSpecial = true, textSizeSp = bigTextSizeSp, hapticFeedback = HapticFeedbackConstants.CONFIRM) { handleEnter() })
        return row
    }

    private fun buildEmojiCategoryTabs(): View {
        val row = horizontalRow()
        EmojiData.CATEGORIES.forEachIndexed { index, category ->
            val label = category.label.take(4)
            row.addView(makeKeyButton(label, 1f, heightDp = 30, isSpecial = true, hapticFeedback = -1) { selectEmojiCategory(index) })
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
                row.addView(makeKeyButton(emoji, 1f, heightDp = 40, isSpecial = false) { handleEmojiTap(emoji) })
            }
            val missing = 4 - emojiRow.size
            for (i in 0 until missing) { row.addView(makeSpacer(1f)) }
            container.addView(row)
        }
        return container
    }

    private fun buildEmojiBottomRow(): View {
        val row = horizontalRow()
        row.addView(makeKeyButton("ABC", 1.6f, isSpecial = true) { switchToLettersMode() })
        row.addView(makeKeyButton("⌫", 1.6f, isSpecial = true, textSizeSp = bigTextSizeSp, hapticFeedback = HapticFeedbackConstants.REJECT) { handleBackspace() })
        row.addView(makeSpaceButton(5f))
        return row
    }

    // ---------------------------------------------------------------
    // Barre d'espace
    // ---------------------------------------------------------------

    private fun makeSpaceButton(weight: Float): Button {
        val button = Button(this).apply {
            text = "espace"
            isAllCaps = false
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minWidth = 0; minHeight = 0; minimumWidth = 0; minimumHeight = 0
            includeFontPadding = false
            stateListAnimator = null
            elevation = 0f
            typeface = sharedTypeface
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, normalTextSizeSp)
            setBackgroundResource(R.drawable.key_bg_special)
            isHapticFeedbackEnabled = true
        }

        val params = LinearLayout.LayoutParams(0, dp(standardKeyHeightDp), weight).apply {
            setMargins(dp(1), dp(1), dp(1), dp(1))
        }
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
            if (isKeyboardFrozen) return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    cursorModeActive = false
                    lastStepX = event.rawX
                    lastStepY = event.rawY
                    view.isPressed = true
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
                    view.isPressed = false
                    if (cursorModeActive) {
                        button.setBackgroundResource(R.drawable.key_bg_special)
                    } else {
                        handleSpace()
                        view.post {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            triggerGlowFlash(view)
                        }
                    }
                    cursorModeActive = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    view.isPressed = false
                    button.setBackgroundResource(R.drawable.key_bg_special)
                    cursorModeActive = false
                    true
                }
                else -> false
            }
        }
    }

    private fun attachShiftBehavior(button: Button) {
        var vaultTriggered = false
        val vaultRunnable = Runnable {
            vaultTriggered = true
            button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showVaultPopup()
        }

        button.setOnTouchListener { view, event ->
            if (isKeyboardFrozen) return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    vaultTriggered = false
                    view.isPressed = true
                    longPressHandler.postDelayed(vaultRunnable, vaultLongPressMs)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(vaultRunnable)
                    view.isPressed = false
                    if (!vaultTriggered) {
                        handleShiftTap()
                        view.post {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            triggerGlowFlash(view)
                        }
                    }
                    vaultTriggered = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(vaultRunnable)
                    view.isPressed = false
                    vaultTriggered = false
                    true
                }
                else -> false
            }
        }
    }

    private fun processCursorDrag(currentX: Float, currentY: Float, lastStepX: Float, lastStepY: Float, onStepConsumed: (Float, Float) -> Unit) {
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
    // Touche lettre composite : label principal + indicateurs de coin
    // (symbole en bas-droite, chiffre en haut-droite pour la rangée du
    // haut) — répond à la demande de rendre visibles les caractères
    // accessibles par appui long.
    // ---------------------------------------------------------------

    private fun makeLetterKey(letter: String, topRowIndex: Int?): View {
        val container = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.key_bg_normal)
            isHapticFeedbackEnabled = true
        }

        val mainLabel = TextView(this).apply {
            text = letter.uppercase()
            typeface = sharedTypeface
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, normalTextSizeSp)
            includeFontPadding = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        container.addView(mainLabel)

        val symbol = LongPressSymbols.symbolFor(letter)
        if (symbol != null) {
            val symbolLabel = TextView(this).apply {
                text = symbol
                typeface = sharedTypeface
                setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_corner_text))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, cornerTextSizeSp)
                includeFontPadding = false
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                ).apply { setMargins(0, 0, dp(3), dp(2)) }
            }
            container.addView(symbolLabel)
        }

        if (topRowIndex != null) {
            val numberLabel = TextView(this).apply {
                text = topRowDigits[topRowIndex]
                typeface = sharedTypeface
                setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_corner_text))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, cornerTextSizeSp)
                includeFontPadding = false
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply { setMargins(0, dp(1), dp(3), 0) }
            }
            container.addView(numberLabel)
        }

        val params = LinearLayout.LayoutParams(0, dp(standardKeyHeightDp), 1f).apply {
            setMargins(dp(1), dp(1), dp(1), dp(1))
        }
        container.layoutParams = params

        attachLetterKeyBehavior(container, letter, topRowIndex)
        return container
    }

    /**
     * Stratégie anti-perte de frappe : le texte est committé en tout
     * premier sur ACTION_DOWN, avant tout effet secondaire (vibration,
     * flash, bulle d'aperçu), qui sont eux différés via view.post pour
     * ne jamais retarder la réception du prochain événement tactile.
     * Sous 90ms depuis la dernière frappe, on saute même les effets non
     * essentiels pour rester fluide en frappe très rapide.
     */
    private fun attachLetterKeyBehavior(container: FrameLayout, letter: String, topRowIndex: Int?) {
        var longPressTriggered = false

        val longPressRunnable = Runnable {
            longPressTriggered = true
            dismissPreview()
            currentInputConnection?.deleteSurroundingText(1, 0)
            container.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showSymbolPopup(container, letter, topRowIndex)
        }

        container.setOnTouchListener { view, event ->
            if (isKeyboardFrozen) return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressTriggered = false
                    view.isPressed = true

                    val now = System.currentTimeMillis()
                    val fastTyping = (now - lastKeystrokeTimeMs) < 90L
                    lastKeystrokeTimeMs = now

                    if (gameState.bossActive) {
                        handleBossKeystroke(letter)
                    } else {
                        handleLetterTap(letter)
                        onSuccessfulKeystroke()
                    }

                    if (!fastTyping) {
                        view.post {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            triggerGlowFlash(view)
                            showPreview(view, letter.uppercase())
                        }
                    }

                    longPressHandler.postDelayed(longPressRunnable, longPressDelayMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longPressTriggered) { updateHoverZone(event.rawX) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    view.isPressed = false
                    dismissPreview()
                    if (longPressTriggered) { commitHighlightedZone() }
                    longPressTriggered = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    view.isPressed = false
                    dismissPreview()
                    if (longPressTriggered) { dismissPopup() }
                    longPressTriggered = false
                    true
                }
                else -> false
            }
        }
    }

    private fun handleBossKeystroke(letter: String) {
        when (gameState.submitBossLetter(letter)) {
            BossResult.SUCCESS -> { updateBossProgressDisplay(); onBossSuccess() }
            BossResult.FAIL -> onBossFail()
            BossResult.CONTINUE -> updateBossProgressDisplay()
        }
    }

    // ---------------------------------------------------------------
    // Popup symbole / chiffre
    // ---------------------------------------------------------------

    private fun showSymbolPopup(anchor: View, letter: String, topRowIndex: Int?) {
        dismissPopup()

        val symbol = LongPressSymbols.symbolFor(letter) ?: return
        val zoneValues = mutableListOf(symbol)
        if (topRowIndex != null) { zoneValues.add(topRowDigits[topRowIndex]) }

        val popupContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ContextCompat.getDrawable(this@NyavoInputMethodService, R.drawable.keyboard_card_bg)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        val zoneButtons = mutableListOf<PopupZone>()
        zoneValues.forEachIndexed { index, value ->
            val zoneBtn = Button(this).apply {
                text = value
                isAllCaps = false
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                minWidth = 0; minHeight = 0; minimumWidth = 0; minimumHeight = 0
                includeFontPadding = false
                stateListAnimator = null
                elevation = 0f
                typeface = sharedTypeface
                setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
                setBackgroundResource(if (index == 0) R.drawable.key_bg_shift else R.drawable.key_bg_special)
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    setMargins(dp(1), dp(1), dp(1), dp(1))
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

        val popup = PopupWindow(popupContainer, measuredWidth, measuredHeight, false).apply {
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
        if (currentZones.isNotEmpty() && currentHighlightIndex in currentZones.indices) {
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
        currentHighlightIndex = 0
    }

    // ---------------------------------------------------------------
    // Saisie & États Standard
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

    private fun handleShiftTap() = state.onShiftTapped().also { updateShiftButtonStyle() }
    private fun cycleLayout() = state.cycleLayout().also { render() }
    private fun switchToEmojiMode() = state.run { mode = KeyboardMode.EMOJI }.also { currentEmojiCategoryIndex = 0; render() }
    private fun switchToLettersMode() = state.run { mode = KeyboardMode.LETTERS }.also { render() }
    private fun selectEmojiCategory(index: Int) { currentEmojiCategoryIndex = index; render() }
    private fun handleSpace() { currentInputConnection?.commitText(" ", 1) }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        breakComboWithFeedbackIfBackspaceSpams()
    }

    private var lastBackspaceTimeMs = 0L
    private fun breakComboWithFeedbackIfBackspaceSpams() {
        val now = System.currentTimeMillis()
        if (now - lastBackspaceTimeMs < 400L && gameState.comboCount > 0) {
            gameState.breakCombo()
            updateComboLabelStyle()
        }
        lastBackspaceTimeMs = now
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

    private fun updateShiftButtonStyle() {
        val button = shiftButton ?: return
        when (state.shiftState) {
            ShiftState.OFF -> { button.text = "⇧"; button.setBackgroundResource(R.drawable.key_bg_special) }
            ShiftState.SHIFT -> { button.text = "⇧"; button.setBackgroundResource(R.drawable.key_bg_shift) }
            ShiftState.CAPS_LOCK -> { button.text = "⇪"; button.setBackgroundResource(R.drawable.key_bg_capslock) }
        }
    }

    private fun layoutAbbreviation(type: KeyboardLayoutType): String = when (type) {
        KeyboardLayoutType.AZERTY -> "AZE"
        KeyboardLayoutType.QWERTY -> "QWE"
        KeyboardLayoutType.QWERTZ -> "QWZ"
    }

    // ---------------------------------------------------------------
    // VAULT MODE (Coffre-Fort Chiffré)
    // ---------------------------------------------------------------

    private fun isPasswordField(): Boolean {
        val inputType = currentInputEditorInfo?.inputType ?: return false
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        return when (inputType and EditorInfo.TYPE_MASK_CLASS) {
            EditorInfo.TYPE_CLASS_TEXT -> variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
            EditorInfo.TYPE_CLASS_NUMBER -> variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun getVaultKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(vaultKeyAlias)) {
            KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(vaultKeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
                generateKey()
            }
        }
        return (keyStore.getEntry(vaultKeyAlias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encryptVault(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, getVaultKey()) }
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    private fun decryptVault(encrypted: String): String {
        val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val cipherText = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, getVaultKey(), GCMParameterSpec(128, iv))
        }
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun showVaultPopup() {
        dismissVaultPopup()
        dismissAddCredentialPopup()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = ContextCompat.getDrawable(this@NyavoInputMethodService, R.drawable.keyboard_card_bg)
        }

        val title = TextView(this).apply {
            text = "🔐 Vault Nyavo"
            textSize = 20f
            typeface = sharedTypeface
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            setPadding(0, 0, 0, dp(12))
        }
        container.addView(title)

        val scroll = android.widget.ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val allEntries = vaultPrefs.all
        var hasEntries = false
        if (allEntries.isNotEmpty()) {
            for ((alias, encrypted) in allEntries) {
                if (encrypted !is String) continue
                try {
                    val password = decryptVault(encrypted)
                    val btn = Button(this).apply {
                        text = alias
                        isAllCaps = false
                        gravity = Gravity.CENTER
                        setPadding(0, 0, 0, 0)
                        minWidth = 0; minHeight = 0
                        includeFontPadding = false
                        stateListAnimator = null
                        elevation = 0f
                        typeface = sharedTypeface
                        setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
                        setBackgroundResource(R.drawable.key_bg_special)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
                            setMargins(0, dp(4), 0, dp(4))
                        }
                        setOnClickListener {
                            currentInputConnection?.commitText(password, 1)
                            dismissVaultPopup()
                        }
                    }
                    list.addView(btn)
                    hasEntries = true
                } catch (e: Exception) { }
            }
        }

        if (!hasEntries) {
            val emptyText = TextView(this).apply {
                text = "Aucun secret. Appui long 3s sur ⇧ pour ajouter."
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(16), dp(8), dp(16))
                setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            }
            list.addView(emptyText)
        }

        scroll.addView(list)
        container.addView(scroll)

        val addBtn = Button(this).apply {
            text = "+ Ajouter un secret"
            isAllCaps = false
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minWidth = 0; minHeight = 0
            includeFontPadding = false
            stateListAnimator = null
            elevation = 0f
            typeface = sharedTypeface
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            setBackgroundResource(R.drawable.key_bg_shift)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)).apply {
                setMargins(0, dp(12), 0, 0)
            }
            setOnClickListener { showAddCredentialDialog() }
        }
        container.addView(addBtn)

        val popup = PopupWindow(container, dp(300), LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isTouchable = true
            setBackgroundDrawable(null)
        }
        showPopupAboveKeyboard(popup, container, 300)
        vaultPopup = popup
    }

    private fun dismissVaultPopup() {
        vaultPopup?.dismiss()
        vaultPopup = null
    }

    private fun showAddCredentialDialog() {
        dismissVaultPopup()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = ContextCompat.getDrawable(this@NyavoInputMethodService, R.drawable.keyboard_card_bg)
        }

        val title = TextView(this).apply {
            text = "Nouveau secret"
            textSize = 18f
            typeface = sharedTypeface
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            setPadding(0, 0, 0, dp(12))
        }
        container.addView(title)

        val inputAlias = android.widget.EditText(this).apply {
            hint = "Nom (ex: GitHub)"
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            setHintTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text).and(0x80FFFFFF.toInt()))
            background = ContextCompat.getDrawable(this@NyavoInputMethodService, R.drawable.key_bg_normal)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        container.addView(inputAlias)

        val inputPass = android.widget.EditText(this).apply {
            hint = "Mot de passe"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            setHintTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text).and(0x80FFFFFF.toInt()))
            background = ContextCompat.getDrawable(this@NyavoInputMethodService, R.drawable.key_bg_normal)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        container.addView(inputPass)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }

        val saveBtn = Button(this).apply {
            text = "Sauver"
            isAllCaps = false
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minWidth = 0; minHeight = 0
            includeFontPadding = false
            stateListAnimator = null
            elevation = 0f
            typeface = sharedTypeface
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            setBackgroundResource(R.drawable.key_bg_shift)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(0, 0, dp(4), 0)
            }
            setOnClickListener {
                val alias = inputAlias.text.toString().trim()
                val pass = inputPass.text.toString()
                if (alias.isNotEmpty() && pass.isNotEmpty()) {
                    vaultPrefs.edit().putString(alias, encryptVault(pass)).apply()
                    dismissAddCredentialPopup()
                    showVaultPopup()
                }
            }
        }

        val cancelBtn = Button(this).apply {
            text = "Annuler"
            isAllCaps = false
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minWidth = 0; minHeight = 0
            includeFontPadding = false
            stateListAnimator = null
            elevation = 0f
            typeface = sharedTypeface
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            setBackgroundResource(R.drawable.key_bg_special)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(4), 0, 0, 0)
            }
            setOnClickListener { dismissAddCredentialPopup() }
        }

        btnRow.addView(saveBtn)
        btnRow.addView(cancelBtn)
        container.addView(btnRow)

        val popup = PopupWindow(container, dp(300), LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isTouchable = true
            setBackgroundDrawable(null)
        }
        showPopupAboveKeyboard(popup, container, 300)
        addCredentialPopup = popup
    }

    private fun dismissAddCredentialPopup() {
        addCredentialPopup?.dismiss()
        addCredentialPopup = null
    }

    // ---------------------------------------------------------------
    // Générateurs Dynamiques de Vues
    // ---------------------------------------------------------------

    private fun verticalContainer() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun horizontalRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun makeKeyButton(
        label: String,
        weight: Float,
        heightDp: Int = standardKeyHeightDp,
        isSpecial: Boolean = false,
        textSizeSp: Float = normalTextSizeSp,
        hapticFeedback: Int = HapticFeedbackConstants.KEYBOARD_TAP,
        onClick: () -> Unit
    ): Button {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minWidth = 0; minHeight = 0; minimumWidth = 0; minimumHeight = 0
            includeFontPadding = false
            stateListAnimator = null
            elevation = 0f
            typeface = sharedTypeface
            setTextColor(ContextCompat.getColor(this@NyavoInputMethodService, R.color.key_text))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            setBackgroundResource(if (isSpecial) R.drawable.key_bg_special else R.drawable.key_bg_normal)
            isHapticFeedbackEnabled = true
        }

        val params = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
            setMargins(dp(1), dp(1), dp(1), dp(1))
        }
        button.layoutParams = params

        button.setOnClickListener {
            if (isKeyboardFrozen) return@setOnClickListener
            onClick()
            it.post {
                if (hapticFeedback != -1) {
                    it.performHapticFeedback(hapticFeedback)
                }
                triggerGlowFlash(it)
            }
        }
        return button
    }

    private fun makeSpacer(weight: Float) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(standardKeyHeightDp), weight)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
