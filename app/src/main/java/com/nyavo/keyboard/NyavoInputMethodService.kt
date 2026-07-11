package com.nyavo.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button

class NyavoInputMethodService : InputMethodService() {

    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)
        setupKeyListeners(keyboardView)
        return keyboardView
    }

    private fun setupKeyListeners(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setupKeyListeners(view.getChildAt(i))
            }
            return
        }
        if (view is Button) {
            when (view.tag as? String) {
                "backspace" -> view.setOnClickListener { handleBackspace() }
                "space" -> view.setOnClickListener { handleSpace() }
                "enter" -> view.setOnClickListener { handleEnter() }
                else -> {
                    val letter = view.text.toString()
                    view.setOnClickListener { handleLetter(letter) }
                }
            }
        }
    }

    private fun handleLetter(letter: String) {
        currentInputConnection?.commitText(letter, 1)
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
}
