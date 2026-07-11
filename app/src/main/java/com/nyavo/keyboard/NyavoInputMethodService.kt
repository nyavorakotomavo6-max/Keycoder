package com.nyavo.keyboard

import android.content.Context
import android.graphics.*
import android.inputmethodservice.InputMethodService
import android.os.*
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.ViewCompat

// ═══════════════════════════════════════════════════════════════════════════
// NYAVO KEYBOARD — Input Method Service
// ═══════════════════════════════════════════════════════════════════════════
// Clavier Android moderne avec support AZERTY/QWERTY/QWERTZ, emojis, et
// symboles de programmation accessibles par appui long.
// API 21+ | Kotlin uniquement | Aucune dépendance externe
// ═══════════════════════════════════════════════════════════════════════════

// ───────────────────────────────────────────────────────────────────────────
// CONSTANTES GLOBALES
// ───────────────────────────────────────────────────────────────────────────

/** Durée d'appui long (ms) avant insertion du symbole secondaire. */
private const val LONG_PRESS_DURATION_MS: Long = 350L

/** Délai avant répétition du retour arrière en maintien (ms). */
private const val BACKSPACE_REPEAT_DELAY_MS: Long = 400L

/** Intervalle entre répétitions du retour arrière (ms). */
private const val BACKSPACE_REPEAT_INTERVAL_MS: Long = 50L

/** Nombre de colonnes dans la grille d'emojis. */
private const val EMOJI_COLUMNS: Int = 8

/** Marge interne du clavier (px). */
private const val KEYBOARD_PADDING_DP: Float = 4f

/** Espacement entre les touches (px). */
private const val KEY_MARGIN_DP: Float = 3f

/** Hauteur d'une touche (dp). */
private const val KEY_HEIGHT_DP: Float = 48f

/** Rayon d'arrondi des touches (dp). */
private const val KEY_CORNER_RADIUS_DP: Float = 6f

/** Taille du texte des touches lettres (sp). */
private const val KEY_TEXT_SIZE_SP: Float = 22f

/** Taille du texte des touches spéciales (sp). */
private const val SPECIAL_KEY_TEXT_SIZE_SP: Float = 16f

/** Taille du texte des emojis (sp). */
private const val EMOJI_TEXT_SIZE_SP: Float = 26f

/** Opacité de la touche pressée (0-255). */
private const val PRESSED_KEY_ALPHA: Int = 80

// ───────────────────────────────────────────────────────────────────────────
// MAP DES SYMBOLES LONG-PRESS
// ───────────────────────────────────────────────────────────────────────────

/**
 * Association lettre (majuscule) → symbole de programmation.
 * Accessible par appui long sans popup ni fenêtre.
 */
private val LONG_PRESS_SYMBOLS: Map<Char, String> = mapOf(
    'A' to "@",
    'S' to "$",
    'D' to "{",
    'F' to "}",
    'G' to "#",
    'H' to "~",
    'J' to "(",
    'K' to ")",
    'L' to ";",
    'Q' to "<",
    'W' to ">",
    'E' to "=",
    'R' to "+",
    'T' to "*",
    'Y' to "|",
    'U' to "_",
    'I' to "[",
    'O' to "]",
    'P' to "\\",
    'Z' to "%",
    'X' to "^",
    'C' to "&",
    'V' to "!",
    'B' to "?",
    'N' to ":",
    'M' to ";"
)

// ───────────────────────────────────────────────────────────────────────────
// ÉNUMÉRATIONS
// ───────────────────────────────────────────────────────────────────────────

/** Mode d'affichage actuel du clavier. */
private enum class KeyboardMode {
    /** Mode saisie de lettres. */
    LETTERS,
    /** Mode sélection d'emojis. */
    EMOJI
}

/** Disposition du clavier alphabétique. */
private enum class KeyboardDisposition {
    /** Disposition française AZERTY. */
    AZERTY,
    /** Disposition internationale QWERTY. */
    QWERTY,
    /** Disposition allemande QWERTZ. */
    QWERTZ
}

/** Catégorie d'une touche du clavier. */
private enum class KeyType {
    /** Touche alphabétique standard. */
    LETTER,
    /** Touche Majuscule (Shift/Caps Lock). */
    SHIFT,
    /** Touche Retour arrière. */
    BACKSPACE,
    /** Touche Entrée. */
    ENTER,
    /** Touche Espace. */
    SPACE,
    /** Touche de changement de disposition. */
    LAYOUT_SWITCH,
    /** Touche de bascule mode Emoji. */
    EMOJI_SWITCH
}

// ───────────────────────────────────────────────────────────────────────────
// MODÈLE DE DONNÉES — TOUCHE
// ───────────────────────────────────────────────────────────────────────────

/**
 * Représente une touche du clavier virtuel.
 *
 * @property type Catégorie de la touche.
 * @property label Texte affiché sur la touche.
 * @property code Code caractère (pour les lettres) ou code touche spéciale.
 * @property widthWeight Poids relatif de largeur (1.0 = standard).
 * @property isRepeatable Si `true`, la touche se répète en cas de maintien.
 */
private data class NyavoKey(
    val type: KeyType,
    val label: String,
    val code: Int = 0,
    val widthWeight: Float = 1f,
    val isRepeatable: Boolean = false
)

// ───────────────────────────────────────────────────────────────────────────
// INTERFACE DE CALLBACK
// ───────────────────────────────────────────────────────────────────────────

/**
 * Interface de communication entre la vue du clavier et le service IME.
 * Déclarée à l'intérieur de la vue pour la cohésion.
 */
private interface KeyboardListener {
    /** Appelé lors d'un appui court sur une touche lettre. */
    fun onLetterKey(charCode: Int, label: String)

    /** Appelé lors d'un appui long sur une touche lettre (symbole). */
    fun onLongPressSymbol(symbol: String)

    /** Appelé lors d'un appui sur Shift. */
    fun onShiftPress()

    /** Appelé lors d'un appui sur Retour arrière. */
    fun onBackspacePress()

    /** Appelé lors du maintien de Retour arrière (répétition). */
    fun onBackspaceRepeat()

    /** Appelé lors d'un appui sur Entrée. */
    fun onEnterPress()

    /** Appelé lors d'un appui sur Espace. */
    fun onSpacePress()

    /** Appelé lors d'un appui sur le bouton de disposition. */
    fun onLayoutSwitch()

    /** Appelé lors d'un appui sur le bouton Emoji (bascule mode). */
    fun onEmojiToggle()

    /** Appelé lors de la sélection d'un emoji. */
    fun onEmojiSelected(emoji: String)
}

// ═══════════════════════════════════════════════════════════════════════════
// VUE PERSONNALISÉE DU CLAVIER
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Vue principale du clavier Nyavo.
 *
 * Gère intégralement le rendu graphique (via Canvas), la détection des
 * touches [onTouchEvent], les appuis longs, et la construction des
 * dispositions. Aucune sous-vue n'est créée : le dessin est entièrement
 * maîtrisé pour des performances maximales.
 *
 * @param context Contexte Android.
 */
private class NyavoKeyboardView(context: Context) : View(context) {

    // ── Listener ──────────────────────────────────────────────────────────

    /** Callback vers le service IME. */
    var listener: KeyboardListener? = null

    // ── État interne ──────────────────────────────────────────────────────

    /** Mode actuel (lettres ou emoji). */
    private var currentMode: KeyboardMode = KeyboardMode.LETTERS

    /** Disposition alphabétique courante. */
    private var currentDisposition: KeyboardDisposition = KeyboardDisposition.AZERTY

    /** `true` si Shift est actif (prochaine lettre en majuscule). */
    private var isShifted: Boolean = false

    /** `true` si Caps Lock est verrouillé. */
    private var isCapsLock: Boolean = false

    /** Liste des touches actuellement affichées. */
    private var keys: List<NyavoKey> = emptyList()

    /** Rectangles de chaque touche (indexé avec [keys]). */
    private var keyRects: List<RectF> = emptyList()

    /** Index de la touche actuellement pressée, `-1` si aucune. */
    private var pressedKeyIndex: Int = -1

    /** `true` si un appui long a été consommé (évite l'action up). */
    private var longPressConsumed: Boolean = false

    /** Page courante des emojis (pagination). */
    private var emojiPage: Int = 0

    // ── Paint (réutilisés, alloués une seule fois) ────────────────────────

    private val paintKeyBackground: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF424242")
        style = Paint.Style.FILL
    }

    private val paintKeyPressed: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF616161")
        style = Paint.Style.FILL
    }

    private val paintKeyStroke: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF212121")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val paintText: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private val paintSpecialText: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEEEEEE")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val paintEmojiText: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    // ── Handler pour appui long et répétition ─────────────────────────────

    private val handler: Handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var backspaceRepeatRunnable: Runnable? = null

    // ── Métriques de mise en page (calculées une fois) ────────────────────

    private var keyHeightPx: Float = 0f
    private var keyMarginPx: Float = 0f
    private var keyboardPaddingPx: Float = 0f
    private var keyCornerRadiusPx: Float = 0f

    // ── Liste des emojis ──────────────────────────────────────────────────

    private val emojis: List<String> = listOf(
        "😀", "😂", "🥰", "😍", "😎", "🤔", "😢", "😡",
        "👍", "👎", "👏", "🙏", "🤝", "✌️", "🤞", "🤟",
        "❤️", "💔", "💖", "💙", "💚", "💛", "💜", "🖤",
        "🔥", "✨", "🎉", "💯", "⭐", "🌟", "💫", "⚡",
        "🐱", "🐶", "🦊", "🐼", "🐨", "🦁", "🐯", "🐷",
        "🌸", "🌺", "🌹", "🌻", "🌿", "🍀", "🍁", "🍄",
        "🍕", "🍔", "🍟", "🌮", "🍦", "🍩", "🍪", "🎂",
        "⚽", "🏀", "🏈", "🎾", "🎮", "🎯", "🎲", "🎸"
    )

    // ══════════════════════════════════════════════════════════════════════
    // INITIALISATION
    // ══════════════════════════════════════════════════════════════════════

    init {
        computeMetrics()
        buildLayout()
        isClickable = true
        isFocusable = true
    }

    /**
     * Calcule les dimensions en pixels à partir des constantes dp.
     * Appelé une seule fois lors de l'initialisation.
     */
    private fun computeMetrics() {
        val density: Float = resources.displayMetrics.density
        keyHeightPx = KEY_HEIGHT_DP * density
        keyMarginPx = KEY_MARGIN_DP * density
        keyboardPaddingPx = KEYBOARD_PADDING_DP * density
        keyCornerRadiusPx = KEY_CORNER_RADIUS_DP * density
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONSTRUCTION DES DISPOSITIONS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Reconstruit la liste des touches en fonction du mode et de la disposition.
     * Cette fonction est le point d'entrée unique pour toute reconstruction.
     */
    private fun buildLayout() {
        keys = when (currentMode) {
            KeyboardMode.LETTERS -> buildLetterLayout()
            KeyboardMode.EMOJI   -> buildEmojiLayout()
        }
        computeKeyRects()
        invalidate()
    }

    /**
     * Construit la disposition alphabétique complète :
     * 3 rangées de lettres + rangée inférieure (contrôles).
     */
    private fun buildLetterLayout(): List<NyavoKey> {
        val result = mutableListOf<NyavoKey>()
        val letters: Array<String> = resolveLetterRows()

        // ── Rangée 1 ──
        letters.getOrNull(0)?.forEach { char ->
            result.add(createLetterKey(char))
        }

        // ── Rangée 2 ──
        letters.getOrNull(1)?.forEach { char ->
            result.add(createLetterKey(char))
        }

        // ── Rangée 3 ──
        buildThirdRow(result, letters)

        // ── Rangée inférieure (contrôles) ──
        result.addAll(buildBottomRow())

        return result
    }

    /**
     * Résolve les trois rangées de lettres selon la disposition courante.
     *
     * @return Tableau de 3 chaînes représentant chaque rangée.
     */
    private fun resolveLetterRows(): Array<String> = when (currentDisposition) {
        KeyboardDisposition.AZERTY -> arrayOf("AZERTYUIOP", "QSDFGHJKLM", "WXCVBN")
        KeyboardDisposition.QWERTY -> arrayOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
        KeyboardDisposition.QWERTZ -> arrayOf("QWERTZUIOP", "ASDFGHJKL", "YXCVBNM")
    }

    /**
     * Construit la troisième rangée avec shift + lettres + backspace.
     *
     * @param target Liste mutable recevant les touches.
     * @param letters Tableau des rangées de lettres.
     */
    private fun buildThirdRow(target: MutableList<NyavoKey>, letters: Array<String>) {
        val shiftLabel: String = if (isCapsLock) "⇪" else "⇧"
        val shiftWeight: Float = 1.5f

        target.add(NyavoKey(KeyType.SHIFT, shiftLabel, widthWeight = shiftWeight))

        letters.getOrNull(2)?.forEach { char ->
            target.add(createLetterKey(char))
        }

        target.add(NyavoKey(KeyType.BACKSPACE, "⌫", widthWeight = 1.5f, isRepeatable = true))
    }

    /**
     * Construit la rangée inférieure avec toutes les touches de contrôle.
     *
     * @return Liste des touches de la rangée du bas.
     */
    private fun buildBottomRow(): List<NyavoKey> = listOf(
        NyavoKey(KeyType.LAYOUT_SWITCH, currentDisposition.name, widthWeight = 1.5f),
        NyavoKey(KeyType.SPACE, "", widthWeight = 5f),
        NyavoKey(KeyType.ENTER, "↵", widthWeight = 1.5f),
        NyavoKey(KeyType.EMOJI_SWITCH, "😀", widthWeight = 1.2f)
    )

    /**
     * Fabrique une touche alphabétique simple.
     *
     * @param char Caractère de la touche (affiché en majuscule).
     * @return Touche configurée.
     */
    private fun createLetterKey(char: Char): NyavoKey {
        val label: String = char.uppercaseChar().toString()
        val code: Int = char.lowercaseChar().code
        return NyavoKey(KeyType.LETTER, label, code)
    }

    /**
     * Construit la disposition en mode Emoji : grille paginée.
     */
    private fun buildEmojiLayout(): List<NyavoKey> {
        val result = mutableListOf<NyavoKey>()
        val startIndex: Int = emojiPage * EMOJI_COLUMNS
        val endIndex: Int = (startIndex + EMOJI_COLUMNS).coerceAtMost(emojis.size)

        for (i: Int in startIndex until endIndex) {
            result.add(NyavoKey(KeyType.LETTER, emojis[i], code = emojis[i].codePointAt(0)))
        }

        // Touches de navigation
        result.addAll(buildEmojiNavigationRow())
        return result
    }

    /**
     * Rangée de navigation en mode Emoji (précédent/suivant/retour).
     */
    private fun buildEmojiNavigationRow(): List<NyavoKey> = listOf(
        NyavoKey(KeyType.LETTER, "◀", code = -10, widthWeight = 1.5f),
        NyavoKey(KeyType.SPACE, "", widthWeight = 4f),
        NyavoKey(KeyType.LETTER, "▶", code = -11, widthWeight = 1.5f),
        NyavoKey(KeyType.EMOJI_SWITCH, "ABC", widthWeight = 1.5f)
    )

    // ══════════════════════════════════════════════════════════════════════
    // CALCUL DES RECTANGLES DE TOUCHES
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Calcule les rectangles de chaque touche pour le rendu et la détection.
     * Doit être appelé après chaque modification de [keys].
     */
    private fun computeKeyRects() {
        if (width == 0 || height == 0) return

        val rects = mutableListOf<RectF>()
        val usableWidth: Float = width - 2 * keyboardPaddingPx
        val density: Float = resources.displayMetrics.density

        if (currentMode == KeyboardMode.LETTERS) {
            computeLetterRects(rects, usableWidth)
        } else {
            computeEmojiRects(rects, usableWidth, density)
        }

        keyRects = rects
    }

    /**
     * Calcule les rectangles pour la disposition lettres.
     */
    private fun computeLetterRects(rects: MutableList<RectF>, usableWidth: Float) {
        val letters: Array<String> = resolveLetterRows()
        val row1Count: Int = letters.getOrNull(0)?.length ?: 0
        val row2Count: Int = letters.getOrNull(1)?.length ?: 0
        val row3CountExtra: Int = 2 // shift + backspace
        val row3Count: Int = (letters.getOrNull(2)?.length ?: 0) + row3CountExtra
        val row4Count: Int = 4 // layout, space, enter, emoji

        val row1WeightSum: Float = row1Count.toFloat()
        val row2WeightSum: Float = row2Count.toFloat()
        val row3WeightSum: Float = 1.5f + (letters.getOrNull(2)?.length ?: 0) + 1.5f
        val row4WeightSum: Float = 1.5f + 5f + 1.5f + 1.2f

        var currentY: Float = keyboardPaddingPx

        // Rangée 1
        val row1Height: Float = computeRowHeight(4)
        computeRowRects(rects, 0, row1Count, usableWidth, currentY, row1Height, row1WeightSum)
        currentY += row1Height + keyMarginPx

        // Rangée 2
        computeRowRects(rects, row1Count, row2Count, usableWidth, currentY, row1Height, row2WeightSum)
        currentY += row1Height + keyMarginPx

        // Rangée 3
        computeRowRects(rects, row1Count + row2Count, row3Count, usableWidth, currentY, row1Height, row3WeightSum)
        currentY += row1Height + keyMarginPx

        // Rangée 4
        computeRowRects(rects, row1Count + row2Count + row3Count, row4Count, usableWidth, currentY, row1Height, row4WeightSum)
    }

    /**
     * Calcule les rectangles pour la disposition emoji.
     */
    private fun computeEmojiRects(rects: MutableList<RectF>, usableWidth: Float, density: Float) {
        val emojiKeysCount: Int = keys.count { it.type == KeyType.LETTER && it.code >= 0 }
        val navCount: Int = keys.size - emojiKeysCount

        val emojiRowHeight: Float = keyHeightPx * density
        val emojiRows: Int = (emojiKeysCount + EMOJI_COLUMNS - 1) / EMOJI_COLUMNS
        var currentY: Float = keyboardPaddingPx

        // Grille d'emojis
        val emojiKeyWidth: Float = (usableWidth - (EMOJI_COLUMNS - 1) * keyMarginPx) / EMOJI_COLUMNS
        for (row: Int in 0 until emojiRows) {
            for (col: Int in 0 until EMOJI_COLUMNS) {
                val index: Int = row * EMOJI_COLUMNS + col
                if (index >= emojiKeysCount) break
                val left: Float = keyboardPaddingPx + col * (emojiKeyWidth + keyMarginPx)
                val right: Float = left + emojiKeyWidth
                val bottom: Float = currentY + emojiRowHeight
                rects.add(RectF(left, currentY, right, bottom))
            }
            currentY += emojiRowHeight + keyMarginPx
        }

        // Rangée de navigation
        val navWeightSum: Float = 1.5f + 4f + 1.5f + 1.5f
        val navHeight: Float = keyHeightPx * density * 0.8f
        val navStartIndex: Int = emojiKeysCount
        computeRowRects(rects, navStartIndex, navCount, usableWidth, currentY, navHeight, navWeightSum)
    }

    /**
     * Calcule la hauteur d'une rangée en fonction du nombre total de rangées.
     */
    private fun computeRowHeight(totalRows: Int): Float {
        val totalMargin: Float = (totalRows - 1) * keyMarginPx
        val availableHeight: Float = height - 2 * keyboardPaddingPx - totalMargin
        return availableHeight / totalRows
    }

    /**
     * Calcule les rectangles d'une rangée de touches donnée.
     */
    private fun computeRowRects(
        rects: MutableList<RectF>,
        startIndex: Int,
        count: Int,
        usableWidth: Float,
        y: Float,
        rowHeight: Float,
        weightSum: Float
    ) {
        val totalMarginWidth: Float = (count - 1).coerceAtLeast(0) * keyMarginPx
        val availableForKeys: Float = usableWidth - totalMarginWidth
        var currentX: Float = keyboardPaddingPx

        for (i: Int in 0 until count) {
            val keyIndex: Int = startIndex + i
            if (keyIndex >= keys.size) break

            val weight: Float = keys[keyIndex].widthWeight
            val keyWidth: Float = (weight / weightSum) * availableForKeys
            val right: Float = currentX + keyWidth
            rects.add(RectF(currentX, y, right, y + rowHeight))
            currentX = right + keyMarginPx
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDU GRAPHIQUE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Dessine l'intégralité du clavier.
     * Appelé automatiquement par le système lors d'un [invalidate].
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density: Float = resources.displayMetrics.density

        keys.forEachIndexed { index: Int, key: NyavoKey ->
            if (index >= keyRects.size) return@forEachIndexed
            val rect: RectF = keyRects[index]
            drawKeyBackground(canvas, rect, index == pressedKeyIndex)
            drawKeyLabel(canvas, key, rect, density)
        }
    }

    /**
     * Dessine l'arrière-plan d'une touche (arrondi).
     *
     * @param canvas Canvas de dessin.
     * @param rect Zone de la touche.
     * @param isPressed `true` si la touche est actuellement pressée.
     */
    private fun drawKeyBackground(canvas: Canvas, rect: RectF, isPressed: Boolean) {
        val paint: Paint = if (isPressed) paintKeyPressed else paintKeyBackground
        canvas.drawRoundRect(rect, keyCornerRadiusPx, keyCornerRadiusPx, paint)
        canvas.drawRoundRect(rect, keyCornerRadiusPx, keyCornerRadiusPx, paintKeyStroke)
    }

    /**
     * Dessine le label d'une touche (texte centré).
     */
    private fun drawKeyLabel(canvas: Canvas, key: NyavoKey, rect: RectF, density: Float) {
        val paint: Paint = selectPaintForKey(key, density)
        val centerX: Float = rect.centerX()
        val centerY: Float = rect.centerY() + paint.textSize / 3f
        canvas.drawText(key.label, centerX, centerY, paint)
    }

    /**
     * Sélectionne le Paint approprié selon le type de touche.
     */
    private fun selectPaintForKey(key: NyavoKey, density: Float): Paint = when {
        currentMode == KeyboardMode.EMOJI && key.label.length > 1 && key.code >= 0 -> {
            paintEmojiText.apply { textSize = EMOJI_TEXT_SIZE_SP * density }
        }
        key.type == KeyType.LETTER && key.label.length == 1 -> {
            paintText.apply { textSize = KEY_TEXT_SIZE_SP * density }
        }
        else -> {
            paintSpecialText.apply { textSize = SPECIAL_KEY_TEXT_SIZE_SP * density }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GESTION DES ÉVÉNEMENTS TACTILES
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Intercepte tous les événements tactiles sur le clavier.
     * Gère la détection des touches, l'appui long, et la répétition.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleTouchDown(event.x, event.y)
            MotionEvent.ACTION_MOVE -> handleTouchMove(event.x, event.y)
            MotionEvent.ACTION_UP -> handleTouchUp()
            MotionEvent.ACTION_CANCEL -> handleTouchCancel()
        }
        return true
    }

    /**
     * Gère l'appui initial sur le clavier.
     *
     * @param x Coordonnée X du toucher.
     * @param y Coordonnée Y du toucher.
     */
    private fun handleTouchDown(x: Float, y: Float) {
        val index: Int = findKeyIndexAt(x, y)
        if (index == -1) return

        pressedKeyIndex = index
        longPressConsumed = false
        invalidate()

        val key: NyavoKey = keys[index]
        startLongPressTimer(key)

        if (key.type == KeyType.BACKSPACE && key.isRepeatable) {
            startBackspaceRepeat()
        }

        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /**
     * Gère le déplacement du doigt sur le clavier.
     *
     * @param x Nouvelle coordonnée X.
     * @param y Nouvelle coordonnée Y.
     */
    private fun handleTouchMove(x: Float, y: Float) {
        val newIndex: Int = findKeyIndexAt(x, y)
        if (newIndex != pressedKeyIndex) {
            cancelLongPressTimer()
            pressedKeyIndex = newIndex
            invalidate()
        }
    }

    /**
     * Gère le relâchement du doigt (exécute l'action si pas d'appui long).
     */
    private fun handleTouchUp() {
        cancelLongPressTimer()
        stopBackspaceRepeat()

        val index: Int = pressedKeyIndex
        pressedKeyIndex = -1
        invalidate()

        if (index != -1 && !longPressConsumed) {
            executeKeyAction(keys[index])
        }
    }

    /**
     * Annule l'interaction en cours (ex: interruption système).
     */
    private fun handleTouchCancel() {
        cancelLongPressTimer()
        stopBackspaceRepeat()
        pressedKeyIndex = -1
        invalidate()
    }

    // ── Détection de touche ───────────────────────────────────────────────

    /**
     * Trouve l'index de la touche située aux coordonnées données.
     *
     * @param x Coordonnée X.
     * @param y Coordonnée Y.
     * @return Index dans [keys], ou `-1` si aucune touche trouvée.
     */
    private fun findKeyIndexAt(x: Float, y: Float): Int {
        keyRects.forEachIndexed { index: Int, rect: RectF ->
            if (rect.contains(x, y)) return index
        }
        return -1
    }

    // ── Appui long ────────────────────────────────────────────────────────

    /**
     * Démarre le timer d'appui long pour une touche donnée.
     * Si le timer expire, le symbole secondaire est inséré.
     *
     * @param key Touche concernée.
     */
    private fun startLongPressTimer(key: NyavoKey) {
        if (key.type != KeyType.LETTER) return
        if (currentMode == KeyboardMode.EMOJI) return

        val symbol: String? = LONG_PRESS_SYMBOLS[key.label.firstOrUppercase()]
        if (symbol == null) return

        longPressRunnable = Runnable {
            longPressConsumed = true
            listener?.onLongPressSymbol(symbol)
            pressedKeyIndex = -1
            invalidate()
        }
        handler.postDelayed(longPressRunnable!!, LONG_PRESS_DURATION_MS)
    }

    /**
     * Annule le timer d'appui long en cours.
     */
    private fun cancelLongPressTimer() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    // ── Répétition retour arrière ─────────────────────────────────────────

    /**
     * Démarre la répétition du retour arrière en cas de maintien.
     */
    private fun startBackspaceRepeat() {
        backspaceRepeatRunnable = object : Runnable {
            override fun run() {
                listener?.onBackspaceRepeat()
                handler.postDelayed(this, BACKSPACE_REPEAT_INTERVAL_MS)
            }
        }
        handler.postDelayed(backspaceRepeatRunnable!!, BACKSPACE_REPEAT_DELAY_MS)
    }

    /**
     * Arrête la répétition du retour arrière.
     */
    private fun stopBackspaceRepeat() {
        backspaceRepeatRunnable?.let { handler.removeCallbacks(it) }
        backspaceRepeatRunnable = null
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXÉCUTION DES ACTIONS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Exécute l'action associée à une touche (appui court).
     *
     * @param key Touche activée.
     */
    private fun executeKeyAction(key: NyavoKey) {
        when (key.type) {
            KeyType.LETTER       -> handleLetterKey(key)
            KeyType.SHIFT        -> listener?.onShiftPress()
            KeyType.BACKSPACE    -> listener?.onBackspacePress()
            KeyType.ENTER        -> listener?.onEnterPress()
            KeyType.SPACE        -> listener?.onSpacePress()
            KeyType.LAYOUT_SWITCH -> listener?.onLayoutSwitch()
            KeyType.EMOJI_SWITCH -> listener?.onEmojiToggle()
        }
    }

    /**
     * Gère l'appui sur une touche alphabétique ou emoji.
     *
     * @param key Touche lettre/emoji.
     */
    private fun handleLetterKey(key: NyavoKey) {
        if (currentMode == KeyboardMode.EMOJI) {
            handleEmojiKey(key)
            return
        }

        val charCode: Int = resolveCharCode(key)
        val label: String = charCode.toChar().toString()
        listener?.onLetterKey(charCode, label)
    }

    /**
     * Résout le code caractère final en prenant en compte Shift/Caps Lock.
     */
    private fun resolveCharCode(key: NyavoKey): Int {
        val baseCode: Int = key.code
        val useUppercase: Boolean = isShifted || isCapsLock
        return if (useUppercase) baseCode.toChar().uppercaseChar().code else baseCode
    }

    /**
     * Gère l'appui sur une touche en mode Emoji.
     */
    private fun handleEmojiKey(key: NyavoKey) {
        when {
            // Navigation page précédente
            key.code == -10 && emojiPage > 0 -> {
                emojiPage--
                buildLayout()
            }
            // Navigation page suivante
            key.code == -11 -> {
                val maxPage: Int = (emojis.size - 1) / EMOJI_COLUMNS
                if (emojiPage < maxPage) {
                    emojiPage++
                    buildLayout()
                }
            }
            // Emoji standard
            key.code > 0 -> {
                listener?.onEmojiSelected(key.label)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // MISE À JOUR DE L'ÉTAT (appelées par le service)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Met à jour l'état Shift/Caps Lock et reconstruit si nécessaire.
     */
    fun updateShiftState(shifted: Boolean, capsLock: Boolean) {
        val needRebuild: Boolean = (isShifted != shifted) || (isCapsLock != capsLock)
        isShifted = shifted
        isCapsLock = capsLock
        if (needRebuild && currentMode == KeyboardMode.LETTERS) {
            buildLayout()
        }
    }

    /**
     * Bascule vers la disposition alphabétique suivante (cyclique).
     */
    fun cycleDisposition() {
        currentDisposition = when (currentDisposition) {
            KeyboardDisposition.AZERTY -> KeyboardDisposition.QWERTY
            KeyboardDisposition.QWERTY -> KeyboardDisposition.QWERTZ
            KeyboardDisposition.QWERTZ -> KeyboardDisposition.AZERTY
        }
        if (currentMode == KeyboardMode.LETTERS) {
            buildLayout()
        }
    }

    /**
     * Active le mode Emoji.
     */
    fun showEmojiMode() {
        currentMode = KeyboardMode.EMOJI
        emojiPage = 0
        buildLayout()
    }

    /**
     * Active le mode Lettres.
     */
    fun showLetterMode() {
        currentMode = KeyboardMode.LETTERS
        buildLayout()
    }

    /**
     * Retourne le mode actuel du clavier.
     */
    fun getCurrentMode(): KeyboardMode = currentMode

    // ══════════════════════════════════════════════════════════════════════
    // OVERRIDES
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Recalcule les rectangles lors d'un changement de dimensions.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeKeyRects()
    }

    // ── Utilitaires ───────────────────────────────────────────────────────

    /**
     * Retourne le caractère en majuscule ou le caractère lui-même.
     */
    private fun Char?.firstOrUppercase(): Char = this?.uppercaseChar() ?: '\u0000'
}

// ═══════════════════════════════════════════════════════════════════════════
// SERVICE IME PRINCIPAL
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Service de méthode d'entrée pour le clavier Nyavo.
 *
 * Ce service est le point d'entrée du système Android pour le clavier.
 * Il crée la [NyavoKeyboardView], gère les interactions utilisateur, et
 * communique avec l'application cible via [InputConnection].
 */
class NyavoInputMethodService : InputMethodService(), KeyboardListener {

    // ── Vue du clavier ────────────────────────────────────────────────────

    /** Vue principale du clavier (null si non initialisée). */
    private var keyboardView: NyavoKeyboardView? = null

    // ── État de saisie ────────────────────────────────────────────────────

    /** `true` si Shift est actif pour la prochaine lettre. */
    private var isShifted: Boolean = false

    /** `true` si Caps Lock est verrouillé. */
    private var isCapsLock: Boolean = false

    // ══════════════════════════════════════════════════════════════════════
    // CYCLE DE VIE DU SERVICE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Crée et retourne la vue du clavier affichée à l'utilisateur.
     * Appelé par le système Android lorsque le clavier doit apparaître.
     *
     * @return Vue racine du clavier.
     */
    override fun onCreateInputView(): View {
        val view = NyavoKeyboardView(this)
        view.listener = this
        keyboardView = view
        return view
    }

    /**
     * Appelé à chaque fois que le clavier s'affiche pour un champ de saisie.
     * Réinitialise l'état si nécessaire.
     */
    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetShiftState()
        keyboardView?.invalidate()
    }

    /**
     * Appelé lorsque le clavier est masqué.
     */
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        resetShiftState()
    }

    // ══════════════════════════════════════════════════════════════════════
    // CALLBACKS DU CLAVIER — Implémentation de KeyboardListener
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Insère un caractère alphabétique dans le champ de saisie.
     *
     * @param charCode Code Unicode du caractère.
     * @param label Représentation textuelle du caractère.
     */
    override fun onLetterKey(charCode: Int, label: String) {
        val connection: InputConnection = currentInputConnection ?: return
        connection.commitText(label, 1)
        updateShiftStateAfterInput()
    }

    /**
     * Insère un symbole de programmation via appui long.
     * Aucun popup n'est affiché : l'insertion est directe.
     *
     * @param symbol Symbole à insérer (ex: "@", "$", "{").
     */
    override fun onLongPressSymbol(symbol: String) {
        val connection: InputConnection = currentInputConnection ?: return
        connection.commitText(symbol, 1)
    }

    /**
     * Gère l'appui sur Shift : alterne entre Shift temporaire et Caps Lock.
     * - 1er appui : Shift actif (prochaine lettre en majuscule).
     * - 2ème appui rapide : Caps Lock verrouillé.
     * - Appui suivant : désactivation.
     */
    override fun onShiftPress() {
        when {
            !isShifted && !isCapsLock -> {
                isShifted = true
            }
            isShifted && !isCapsLock -> {
                isShifted = false
                isCapsLock = true
            }
            else -> {
                isShifted = false
                isCapsLock = false
            }
        }
        keyboardView?.updateShiftState(isShifted, isCapsLock)
    }

    /**
     * Supprime le caractère précédent (Retour arrière simple).
     */
    override fun onBackspacePress() {
        val connection: InputConnection = currentInputConnection ?: return
        connection.deleteSurroundingText(1, 0)
    }

    /**
     * Supprime le caractère précédent en mode répétition (maintien).
     */
    override fun onBackspaceRepeat() {
        val connection: InputConnection = currentInputConnection ?: return
        connection.deleteSurroundingText(1, 0)
    }

    /**
     * Envoie l'action Entrée à l'application cible.
     * Déclenche l'action de l'éditeur si disponible.
     */
    override fun onEnterPress() {
        val connection: InputConnection = currentInputConnection ?: return
        if (!sendDefaultEditorAction(true)) {
            connection.commitText("\n", 1)
        }
    }

    /**
     * Insère un espace dans le champ de saisie.
     */
    override fun onSpacePress() {
        val connection: InputConnection = currentInputConnection ?: return
        connection.commitText(" ", 1)
        updateShiftStateAfterInput()
    }

    /**
     * Passe à la disposition alphabétique suivante (AZERTY → QWERTY → QWERTZ).
     */
    override fun onLayoutSwitch() {
        keyboardView?.cycleDisposition()
    }

    /**
     * Bascule entre le mode Lettres et le mode Emoji.
     */
    override fun onEmojiToggle() {
        val view: NyavoKeyboardView = keyboardView ?: return
        if (view.getCurrentMode() == KeyboardMode.LETTERS) {
            view.showEmojiMode()
        } else {
            view.showLetterMode()
        }
    }

    /**
     * Insère l'emoji sélectionné dans le champ de saisie.
     *
     * @param emoji Chaîne Unicode représentant l'emoji.
     */
    override fun onEmojiSelected(emoji: String) {
        val connection: InputConnection = currentInputConnection ?: return
        connection.commitText(emoji, 1)
    }

    // ══════════════════════════════════════════════════════════════════════
    // GESTION DE L'ÉTAT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Met à jour l'état Shift après une saisie de caractère.
     * Shift temporaire est désactivé ; Caps Lock persiste.
     */
    private fun updateShiftStateAfterInput() {
        if (isShifted && !isCapsLock) {
            isShifted = false
            keyboardView?.updateShiftState(isShifted, isCapsLock)
        }
    }

    /**
     * Réinitialise l'état Shift/Caps Lock à sa valeur par défaut.
     */
    private fun resetShiftState() {
        isShifted = false
        isCapsLock = false
        keyboardView?.updateShiftState(false, false)
    }
}
