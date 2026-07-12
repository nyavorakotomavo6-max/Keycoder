package com.nyavo.keyboard

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Rect
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
    private var floatAnimator: ObjectAnimator? = null

    private var activePopupWindow: PopupWindow? = null

    // V2.0 : clavier plus compact
    private val standardKeyHeightDp = 36


    override fun onCreate() {
        super.onCreate()
        state = KeyboardState(this)
    }


    override fun onCreateInputView(): View {

        val root =
            layoutInflater.inflate(
                R.layout.keyboard_view,
                null
            ) as FrameLayout


        val card =
            root.findViewById<LinearLayout>(
                R.id.keyboard_card
            )


        rootContainer = card

        render()

        startFloatingAnimation(card)

        return root
    }


    override fun onStartInputView(
        info: EditorInfo?,
        restarting: Boolean
    ) {
        super.onStartInputView(info, restarting)

        if (floatAnimator?.isRunning == false) {
            floatAnimator?.start()
        }
    }


    override fun onFinishInputView(
        finishingInput: Boolean
    ) {
        super.onFinishInputView(finishingInput)

        floatAnimator?.cancel()

        dismissActivePopup()
    }


    override fun onDestroy() {

        super.onDestroy()

        floatAnimator?.cancel()

        dismissActivePopup()
    }



    // ----------------------------------------------------
    // Animation flottante V2.0
    // ----------------------------------------------------

    private fun startFloatingAnimation(
        target: View
    ) {

        floatAnimator?.cancel()


        floatAnimator =
            ObjectAnimator.ofFloat(
                target,
                "translationY",
                0f,
                -3f,
                0f
            ).apply {

                duration = 2200L

                repeatCount =
                    ValueAnimator.INFINITE

                interpolator =
                    AccelerateDecelerateInterpolator()
            }


        floatAnimator?.start()
    }



    // ----------------------------------------------------
    // Rendu principal
    // ----------------------------------------------------

    private fun render() {

        val root =
            rootContainer ?: return


        root.removeAllViews()


        val content =
            when(state.mode) {

                KeyboardMode.LETTERS ->
                    buildLettersView()


                KeyboardMode.EMOJI ->
                    buildEmojiView()
            }


        root.addView(content)


        if(state.mode == KeyboardMode.LETTERS) {

            updateShiftButtonStyle()

        }
    }



    // ----------------------------------------------------
    // Mode lettres
    // ----------------------------------------------------

    private fun buildLettersView(): View {

        val container =
            verticalContainer()


        val rows =
            KeyboardLayoutData.rowsFor(
                state.layoutType
            )


        container.addView(
            buildLetterRow(rows[0])
        )


        container.addView(
            buildLetterRow(rows[1])
        )


        container.addView(
            buildThirdLetterRow(rows[2])
        )


        container.addView(
            buildPunctuationRow()
        )


        container.addView(
            buildBottomRow()
        )


        return container
    }
    // ----------------------------------------------------
    // Construction des rangées clavier
    // ----------------------------------------------------

    private fun buildLetterRow(
        letters: List<String>
    ): View {

        val row = horizontalRow()

        for (letter in letters) {

            row.addView(
                makeKeyButton(
                    label = letter.uppercase(),
                    weight = 1f,
                    isSpecial = false,
                    onLongClick = {
                        handleLongPressSymbol(letter)
                    },
                    onDoubleClick = {
                        handleDoubleTapLetter(letter)
                    }
                ) {
                    handleLetterTap(letter)
                }
            )
        }

        return row
    }



    private fun buildThirdLetterRow(
        letters: List<String>
    ): View {

        val row = horizontalRow()


        val shift =
            makeKeyButton(
                label = "⇧",
                weight = 1.5f,
                isSpecial = true
            ) {
                handleShiftTap()
            }


        shiftButton = shift

        row.addView(shift)



        for(letter in letters) {

            row.addView(
                makeKeyButton(
                    label = letter.uppercase(),
                    weight = 1f,
                    isSpecial = false,
                    onLongClick = {
                        handleLongPressSymbol(letter)
                    },
                    onDoubleClick = {
                        handleDoubleTapLetter(letter)
                    }
                ) {
                    handleLetterTap(letter)
                }
            )
        }


        row.addView(
            makeKeyButton(
                label = "⌫",
                weight = 1.5f,
                isSpecial = true
            ) {
                handleBackspace()
            }
        )


        return row
    }




    private fun buildPunctuationRow(): View {

        val row = horizontalRow()


        for(symbol in KeyboardLayoutData.QUICK_PUNCTUATION) {

            row.addView(
                makeKeyButton(
                    label = symbol,
                    weight = 1f,
                    isSpecial = false
                ) {

                    handleSymbolTap(symbol)

                }
            )
        }


        return row
    }




    private fun buildBottomRow(): View {

        val row = horizontalRow()


        row.addView(
            makeKeyButton(
                label = "😊",
                weight = 1.2f,
                isSpecial = true
            ) {

                switchToEmojiMode()

            }
        )



        row.addView(
            makeKeyButton(
                label = layoutAbbreviation(
                    state.layoutType
                ),
                weight = 1.2f,
                isSpecial = true
            ) {

                cycleLayout()

            }
        )



        row.addView(
            makeKeyButton(
                label = "espace",
                weight = 5f,
                isSpecial = true
            ) {

                handleSpace()

            }
        )



        row.addView(
            makeKeyButton(
                label = "↵",
                weight = 1.6f,
                isSpecial = true
            ) {

                handleEnter()

            }
        )


        return row
    }



    // ----------------------------------------------------
    // Mode Emoji
    // ----------------------------------------------------

    private fun buildEmojiView(): View {

        val container =
            verticalContainer()


        container.addView(
            buildEmojiCategoryTabs()
        )


        container.addView(
            buildEmojiGrid()
        )


        container.addView(
            buildEmojiBottomRow()
        )


        return container
    }



    private fun buildEmojiCategoryTabs(): View {

        val row =
            horizontalRow()


        EmojiData.CATEGORIES.forEachIndexed {
                index,
                category ->


            row.addView(
                makeKeyButton(
                    label = category.label.take(4),
                    weight = 1f,
                    heightDp = 32,
                    isSpecial = true
                ) {

                    selectEmojiCategory(index)

                }
            )
        }


        return row
    }



    private fun buildEmojiGrid(): View {

        val container =
            verticalContainer()


        val category =
            EmojiData.CATEGORIES[currentEmojiCategoryIndex]


        val rows =
            category.emojis.chunked(4)



        for(emojiRow in rows) {

            val row =
                horizontalRow()


            for(emoji in emojiRow) {

                row.addView(
                    makeKeyButton(
                        label = emoji,
                        weight = 1f,
                        heightDp = 36,
                        isSpecial = false
                    ) {

                        handleEmojiTap(emoji)

                    }
                )
            }


            val missing =
                4 - emojiRow.size


            repeat(missing) {

                row.addView(
                    makeSpacer(1f)
                )

            }


            container.addView(row)
        }


        return container
    }
private fun buildEmojiBottomRow(): View {

    val row = horizontalRow()

    row.addView(
        makeKeyButton(
            label = "ABC",
            weight = 1.6f,
            isSpecial = true
        ) {
            switchToLettersMode()
        }
    )

    row.addView(
        makeKeyButton(
            label = "⌫",
            weight = 1.6f,
            isSpecial = true
        ) {
            handleBackspace()
        }
    )

    row.addView(
        makeKeyButton(
            label = "espace",
            weight = 5f,
            isSpecial = true
        ) {
            handleSpace()
        }
    )

    return row
}
// ----------------------------------------------------
    // Actions clavier
    // ----------------------------------------------------

    private fun handleLetterTap(letter: String) {

        val output =
            if(state.isUppercase())
                letter.uppercase()
            else
                letter


        currentInputConnection?.commitText(
            output,
            1
        )


        state.consumeShiftAfterLetter()

        updateShiftButtonStyle()
    }



    private fun handleDoubleTapLetter(
        letter: String
    ): Boolean {

        val number =
            getNumberForLetter(letter)
                ?: return false


        currentInputConnection?.commitText(
            number,
            1
        )


        return true
    }



    private fun handleLongPressSymbol(
        letter: String
    ) {

        val symbol =
            LongPressSymbols.symbolFor(letter)
                ?: return


        currentInputConnection?.commitText(
            symbol,
            1
        )
    }



    private fun handleSymbolTap(
        symbol: String
    ) {

        currentInputConnection?.commitText(
            symbol,
            1
        )
    }



    private fun handleEmojiTap(
        emoji: String
    ) {

        currentInputConnection?.commitText(
            emoji,
            1
        )
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

        state.mode =
            KeyboardMode.EMOJI

        currentEmojiCategoryIndex = 0

        render()
    }



    private fun switchToLettersMode() {

        state.mode =
            KeyboardMode.LETTERS

        render()
    }



    private fun selectEmojiCategory(
        index: Int
    ) {

        currentEmojiCategoryIndex =
            index

        render()
    }



    private fun handleSpace() {

        currentInputConnection?.commitText(
            " ",
            1
        )
    }



    private fun handleBackspace() {

        currentInputConnection?.deleteSurroundingText(
            1,
            0
        )
    }



    private fun handleEnter() {

        val ic =
            currentInputConnection
                ?: return


        val action =
            currentInputEditorInfo
                ?.imeOptions
                ?.and(EditorInfo.IME_MASK_ACTION)


        if(action != null &&
            action != EditorInfo.IME_ACTION_NONE) {


            ic.performEditorAction(action)

        } else {

            ic.commitText(
                "\n",
                1
            )
        }
    }



    private fun getNumberForLetter(
        letter: String
    ): String? {

        val row =
            KeyboardLayoutData
                .rowsFor(state.layoutType)
                .firstOrNull()
                ?: return null


        val index =
            row.indexOf(letter.lowercase())


        if(index >= 0 &&
            index < 10) {

            return if(index == 9)
                "0"
            else
                (index + 1).toString()
        }


        return null
    }



    // ----------------------------------------------------
    // Style Shift
    // ----------------------------------------------------

    private fun updateShiftButtonStyle() {

        val button =
            shiftButton ?: return


        when(state.shiftState) {

            ShiftState.OFF -> {

                button.text = "⇧"

                button.setBackgroundResource(
                    R.drawable.key_bg_special
                )
            }


            ShiftState.SHIFT -> {

                button.text = "⇧"

                button.setBackgroundResource(
                    R.drawable.key_bg_shift
                )
            }


            ShiftState.CAPS_LOCK -> {

                button.text = "⇪"

                button.setBackgroundResource(
                    R.drawable.key_bg_capslock
                )
            }
        }
    }



    private fun layoutAbbreviation(
        type: KeyboardLayoutType
    ): String {

        return when(type) {

            KeyboardLayoutType.AZERTY ->
                "AZE"

            KeyboardLayoutType.QWERTY ->
                "QWE"

            KeyboardLayoutType.QWERTZ ->
                "QWZ"
        }
    }



    // ----------------------------------------------------
    // Création des vues
    // ----------------------------------------------------

    private fun verticalContainer():
            LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            layoutParams =
                LinearLayout.LayoutParams(
                    -1,
                    -2
                )
        }
    }



    private fun horizontalRow():
            LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.HORIZONTAL

            layoutParams =
                LinearLayout.LayoutParams(
                    -1,
                    -2
                )
        }
    }



    private fun makeKeyButton(
        label: String,
        weight: Float,
        heightDp: Int = standardKeyHeightDp,
        isSpecial: Boolean = false,
        onLongClick: (() -> Unit)? = null,
        onDoubleClick: (() -> Boolean)? = null,
        onClick: () -> Unit

    ): Button {


        val button =
            Button(this)



        button.text = label

        button.isAllCaps = false

        button.gravity =
            Gravity.CENTER


        button.setPadding(
            0,
            0,
            0,
            0
        )


        button.minWidth = 0
        button.minHeight = 0

        button.minimumWidth = 0
        button.minimumHeight = 0


        button.includeFontPadding = false

        button.stateListAnimator = null

        button.elevation = 0f


        button.typeface =
            Typeface.create(
                Typeface.MONOSPACE,
                Typeface.BOLD
            )


        button.setTextColor(
            ContextCompat.getColor(
                this,
                R.color.key_text
            )
        )



        button.setBackgroundResource(
            if(isSpecial)
                R.drawable.key_bg_special
            else
                R.drawable.key_bg_normal
        )



        val params =
            LinearLayout.LayoutParams(
                0,
                dp(heightDp),
                weight
            )


        params.setMargins(
            dpF(1f),
            dpF(1f),
            dpF(1f),
            dpF(1f)
        )


        button.layoutParams =
            params



        var lastClick = 0L



        button.setOnClickListener {

            val now =
                System.currentTimeMillis()


            if(onDoubleClick != null &&
                now - lastClick < 300) {


                if(onDoubleClick()) {

                    lastClick = 0L
                    return@setOnClickListener
                }
            }


            lastClick = now

            onClick()
        }



        onLongClick?.let {

            button.setOnLongClickListener {

                onLongClick()

                true
            }
        }



        attachSinkAnimation(
            button,
            label,
            isSpecial
        )


        return button
    }
    // ----------------------------------------------------
    // Animation touche + popup
    // ----------------------------------------------------

    private fun attachSinkAnimation(
        button: Button,
        label: String,
        isSpecial: Boolean
    ) {

        button.setOnTouchListener { view, event ->


            when(event.action) {


                MotionEvent.ACTION_DOWN -> {


                    view.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP
                    )


                    view.animate()
                        .translationY(2f)
                        .setDuration(40L)
                        .start()



                    if(!isSpecial &&
                        label.isNotEmpty()) {

                        showCharacterPopup(
                            view,
                            label
                        )
                    }
                }



                MotionEvent.ACTION_MOVE -> {


                    val rect =
                        Rect(
                            0,
                            0,
                            view.width,
                            view.height
                        )


                    if(!rect.contains(
                            event.x.toInt(),
                            event.y.toInt()
                        )) {


                        view.animate()
                            .translationY(0f)
                            .setDuration(60L)
                            .start()


                        dismissActivePopup()
                    }
                }



                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {


                    view.animate()
                        .translationY(0f)
                        .setDuration(60L)
                        .start()


                    dismissActivePopup()
                }
            }


            false
        }
    }



    private fun showCharacterPopup(
        anchorView: View,
        text: String
    ) {


        dismissActivePopup()



        val popupText =
            TextView(this).apply {


                this.text = text


                gravity =
                    Gravity.CENTER


                textSize = 22f


                typeface =
                    Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                    )


                setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.key_text
                    )
                )


                setBackgroundResource(
                    R.drawable.key_bg_special
                )


                setPadding(
                    dp(10),
                    dp(4),
                    dp(10),
                    dp(4)
                )
            }




        popupText.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        )



        val popupWidth =
            popupText.measuredWidth



        val popup =
            PopupWindow(
                popupText,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {


                isClippingEnabled = false

                isTouchable = false

                isFocusable = false
            }



        activePopupWindow =
            popup



        popup.showAsDropDown(
            anchorView,
            (anchorView.width - popupWidth) / 2,
            -(anchorView.height + dp(8)),
            Gravity.NO_GRAVITY
        )
    }



    private fun dismissActivePopup() {

        activePopupWindow?.dismiss()

        activePopupWindow = null
    }



    // ----------------------------------------------------
    // Emoji espace vide
    // ----------------------------------------------------

    private fun makeSpacer(
        weight: Float
    ): View {


        return View(this).apply {


            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    dp(standardKeyHeightDp),
                    weight
                )
        }
    }




    // ----------------------------------------------------
    // Conversion DP
    // ----------------------------------------------------

    private fun dp(
        value: Int
    ): Int {


        return (
            value *
            resources.displayMetrics.density
        ).toInt()
    }




    private fun dpF(
        value: Float
    ): Int {


        return (
            value *
            resources.displayMetrics.density
        ).toInt()
    }

}