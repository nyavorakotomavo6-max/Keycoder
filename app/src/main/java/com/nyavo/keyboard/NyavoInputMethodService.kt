package com.nyavo.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat

class NyavoInputMethodService : InputMethodService() {

    private val state = KeyboardState()
    private val doubleTapDetector = DoubleTapDetector()

    // ========== Lifecycle ==========

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val keyboardView = setInputView(createKeyboardView())
        
        // Hauteur = 35% de l'écran
        val keyboardHeight = (resources.displayMetrics.heightPixels * 0.35).toInt()
        keyboardView.layoutParams?.height = keyboardHeight
    }

    override fun onCreateInputView(): View {
        return createKeyboardView()
    }

    private fun createKeyboardView(): View {
        val view = currentInputView ?: layoutInflater.inflate(R.layout.keyboard_view, null)
        buildKeyboard(view)
        return view
    }

    // ========== Construction du clavier ==========

    private fun buildKeyboard(view: View) {
        val row1 = view.findViewById<LinearLayout>(R.id.row1)
        val row2 = view.findViewById<LinearLayout>(R.id.row2)
        val row3 = view.findViewById<LinearLayout>(R.id.row3)
        val rowSpace = view.findViewById<LinearLayout>(R.id.row_space)
        row1.removeAllViews()
        row2.removeAllViews()
        row3.removeAllViews()
        rowSpace.removeAllViews()

        val layout = KeyboardLayoutData.getLayout(state.layoutType)

        // Ligne 1
        layout.row1.forEach { letter ->
            row1.addView(makeKeyButton(letter,
                onClick = { handleKeyPress(letter) },
                onLongClick = { btn -> showLongPressPopup(letter, btn); true }
            ))
        }

        // Ligne 2
        layout.row2.forEach { letter ->
            row2.addView(makeKeyButton(letter,
                onClick = { handleKeyPress(letter) },
                onLongClick = { btn -> showLongPressPopup(letter, btn); true }
            ))
        }

        // Ligne 3
        layout.row3.forEach { letter ->
            row3.addView(makeKeyButton(letter,
                onClick = { handleKeyPress(letter) },
                onLongClick = { btn -> showLongPressPopup(letter, btn); true }
            ))
        }

        // Ligne espace
        rowSpace.addView(makeSpecialButton("⌫", 1f) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        })
        rowSpace.addView(makeSpecialButton(",", 1f) {
            currentInputConnection?.commitText(",", 1)
        })
        rowSpace.addView(makeSpecialButton("␣", 4f) {
            currentInputConnection?.commitText(" ", 1)
        })
        rowSpace.addView(makeSpecialButton(".", 1f) {
            currentInputConnection?.commitText(".", 1)
        })
        rowSpace.addView(makeSpecialButton("↵", 1f) {
            currentInputConnection?.commitText("\n", 1)
        })
    }

    // ========== Création des boutons ==========
    private fun makeKeyButton(
        label: String,
        onClick: (Button) -> Unit,
        onLongClick: ((Button) -> Boolean)? = null
    ): Button {
        return Button(context).apply {
            text = if (state.isShifted) label.uppercase() else label
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isAllCaps = false

            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = 3.dpToPx()
                marginEnd = 3.dpToPx()
            }

            setPadding(8, 8, 8, 8)
            background = ContextCompat.getDrawable(context, R.drawable.key_background)
            stateListAnimator = null

            setOnClickListener { onClick(this) }
            if (onLongClick != null) {
                setOnLongClickListener { onLongClick(this) }
            }
        }
    }

    private fun makeSpecialButton(label: String, weight: Float, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
                marginStart = 3.dpToPx()
                marginEnd = 3.dpToPx()
            }
            background = ContextCompat.getDrawable(context, R.drawable.key_background)
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
    }

    // ========== Gestion des touches ==========

    private fun handleKeyPress(letter: String) {
        val textToCommit = if (state.isShifted) letter.uppercase() else letter
        doubleTapDetector.onTap(
            onSingleTap = {                currentInputConnection?.commitText(textToCommit, 1)
                if (state.isShifted) {
                    state.isShifted = false
                    refreshKeyboard()
                }
            },
            onDoubleTap = {
                handleDoubleTapLetter(letter)
            }
        )
    }

    private fun handleDoubleTapLetter(letter: String) {
        // Double-clic = insertion de chiffres/symboles par défaut
        currentInputConnection?.commitText("1", 1)
    }

    // ========== Popup d'appui long ==========

    private fun showLongPressPopup(letter: String, anchorView: View) {
        val symbols = LongPressSymbols.symbolsFor(letter, state.layoutType)
        if (symbols.isEmpty()) return

        val location = IntArray(2)
        anchorView.getLocationInWindow(location)

        val popupWidth = symbols.size * 120.dpToPx()
        val x = location[0] + (anchorView.width / 2) - (popupWidth / 2)
        val y = location[1] - 140.dpToPx()

        val popupContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.parseColor("#FF1A1A1A"))
        }

        symbols.forEach { symbol ->
            val btn = Button(context).apply {
                text = symbol
                textSize = 20f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    100.dpToPx(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 4.dpToPx()
                    marginEnd = 4.dpToPx()
                }
                background = ContextCompat.getDrawable(context, R.drawable.key_background)
                setOnClickListener {                    currentInputConnection?.commitText(symbol, 1)
                    popup.dismiss()
                }
            }
            popupContent.addView(btn)
        }

        val popup = PopupWindow(
            popupContent,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            setBackgroundDrawable(null)
            isOutsideTouchable = true
        }

        popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y)

        anchorView.postDelayed({
            if (popup.isShowing) popup.dismiss()
        }, 3000)
    }

    // ========== DoubleTapDetector ==========

    private inner class DoubleTapDetector {
        private var lastTapTime = 0L
        private var pendingSingleTap: Runnable? = null
        private val handler = Handler(Looper.getMainLooper())
        private val DOUBLE_TAP_TIMEOUT = 300L

        fun onTap(onSingleTap: () -> Unit, onDoubleTap: () -> Unit) {
            val now = System.currentTimeMillis()

            // Annuler le single tap en attente
            pendingSingleTap?.let { handler.removeCallbacks(it) }

            if (now - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                // Double tap détecté
                onDoubleTap()
                lastTapTime = 0L
            } else {
                // Programmer un single tap
                val runnable = Runnable {
                    onSingleTap()
                    lastTapTime = 0L
                }
                pendingSingleTap = runnable
                handler.postDelayed(runnable, DOUBLE_TAP_TIMEOUT)                lastTapTime = now
            }
        }
    }

    // ========== Helpers ==========

    private fun refreshKeyboard() {
        currentInputView?.let { buildKeyboard(it) }
    }

    private fun Int.dpToPx(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}