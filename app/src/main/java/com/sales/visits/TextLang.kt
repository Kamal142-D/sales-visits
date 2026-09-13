package com.sales.visits

/** Arabic (and Arabic-supplement/presentation) Unicode blocks. */
private fun isArabic(ch: Char): Boolean =
    ch in '؀'..'ۿ' || ch in 'ݐ'..'ݿ' ||
        ch in 'ࢠ'..'ࣿ' || ch in 'ﭐ'..'﷿' || ch in 'ﹰ'..'﻿'

/** True when a string is mostly Arabic (used to route a template into the right note container). */
fun isArabicText(s: String): Boolean {
    val letters = s.count { it.isLetter() }
    if (letters == 0) return false
    return s.count { isArabic(it) } * 2 >= letters
}

/**
 * Returns only the requested-language portion of a (possibly bilingual) note. Each line is
 * classified by its dominant script — Arabic vs. Latin — so an AI note that holds both an Arabic
 * block and an English block is split cleanly without needing another AI call. Reflects the user's
 * own edits because it works on the live text. Falls back to the whole text when the requested
 * language isn't present, so the report is never blank.
 */
fun noteInLanguage(text: String, wantEnglish: Boolean, fallbackWhole: Boolean = true): String {
    if (text.isBlank()) return ""
    val picked = StringBuilder()
    var pendingBlank = false
    for (line in text.split("\n")) {
        val letters = line.count { it.isLetter() }
        if (letters == 0) {
            if (picked.isNotEmpty()) pendingBlank = true
            continue
        }
        val arabic = line.count { isArabic(it) }
        val lineIsArabic = arabic * 2 >= letters      // ≥ 50% Arabic letters ⇒ an Arabic line
        val keep = if (wantEnglish) !lineIsArabic else lineIsArabic
        if (keep) {
            if (pendingBlank) { picked.append("\n"); pendingBlank = false }
            picked.append(line).append("\n")
        }
    }
    val result = picked.toString().trim()
    return if (result.isNotBlank() || !fallbackWhole) result else text.trim()
}
