package com.nyavo.keyboard

import kotlin.random.Random

enum class ComboTier { BASE, GOLD, FIRE, NEON }

class GameState {

    var comboCount: Int = 0
        private set
    private var lastKeyTimeMs: Long = 0L
    private val comboWindowMs = 700L

    private var totalKeystrokes: Long = 0L
    private val bossIntervalKeystrokes = 100L

    var bossActive: Boolean = false
        private set
    var bossWord: String = ""
        private set
    var bossProgress: Int = 0
        private set

    fun tier(): ComboTier = when {
        comboCount >= 200 -> ComboTier.NEON
        comboCount >= 100 -> ComboTier.FIRE
        comboCount >= 50 -> ComboTier.GOLD
        else -> ComboTier.BASE
    }

    /**
     * Appelé à chaque lettre tapée hors combat de boss. Retourne true si
     * le combo continue, false s'il vient d'être rompu (délai dépassé).
     */
    fun registerKeystroke(now: Long): Boolean {
        val continued = (now - lastKeyTimeMs) <= comboWindowMs || comboCount == 0
        comboCount = if (continued) comboCount + 1 else 1
        lastKeyTimeMs = now
        totalKeystrokes++
        return continued || comboCount == 1
    }

    fun breakCombo() {
        comboCount = 0
    }

    fun shouldTriggerBoss(): Boolean {
        return !bossActive && totalKeystrokes > 0 && totalKeystrokes % bossIntervalKeystrokes == 0L
    }

    fun startBoss(): String {
        val alphabet = "AZERTYUIOPQSDFGHJKLMWXCVBN"
        val word = buildString {
            repeat(15) { append(alphabet[Random.nextInt(alphabet.length)]) }
        }
        bossWord = word
        bossProgress = 0
        bossActive = true
        return word
    }

    /**
     * Compare une lettre tapée au caractère attendu du mot du boss.
     * Retourne SUCCESS si le mot est complété, FAIL si la lettre ne
     * correspond pas, CONTINUE sinon.
     */
    fun submitBossLetter(letter: String): BossResult {
        if (!bossActive) return BossResult.CONTINUE
        val expected = bossWord.getOrNull(bossProgress)?.toString() ?: return BossResult.FAIL
        if (!letter.equals(expected, ignoreCase = true)) {
            return BossResult.FAIL
        }
        bossProgress++
        return if (bossProgress >= bossWord.length) BossResult.SUCCESS else BossResult.CONTINUE
    }

    fun endBoss() {
        bossActive = false
        bossWord = ""
        bossProgress = 0
    }

    fun addComboBonus(amount: Int) {
        comboCount += amount
    }
}

enum class BossResult { CONTINUE, SUCCESS, FAIL }