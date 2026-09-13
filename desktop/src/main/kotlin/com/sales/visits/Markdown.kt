package com.sales.visits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val boldRegex = Regex("""\*\*(.+?)\*\*""")

/** Parses inline `**bold**` spans in a line. */
private fun inlineMarkdown(s: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in boldRegex.findAll(s)) {
        append(s.substring(last, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        last = m.range.last + 1
    }
    append(s.substring(last))
}

/**
 * Renders lightweight markdown: `# ` / `## ` headings, `- ` or `• ` bullets, `**bold**`.
 * Anything else is a plain paragraph line. [maxLines] caps how many lines are shown (for cards).
 */
@Composable
fun MarkdownText(text: String, color: Color, baseSize: androidx.compose.ui.unit.TextUnit = 14.sp, maxLines: Int = Int.MAX_VALUE) {
    val c = LocalSales.current
    val lines = text.split("\n").let { if (it.size > maxLines) it.take(maxLines) else it }
    Column {
        lines.forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.startsWith("# ") -> androidx.compose.material3.Text(
                    inlineMarkdown(line.removePrefix("# ")), color = c.ink,
                    fontSize = baseSize * 1.55f, fontWeight = FontWeight.ExtraBold, lineHeight = baseSize * 1.9f,
                )
                line.startsWith("## ") -> androidx.compose.material3.Text(
                    inlineMarkdown(line.removePrefix("## ")), color = c.ink,
                    fontSize = baseSize * 1.25f, fontWeight = FontWeight.Bold, lineHeight = baseSize * 1.6f,
                )
                line.startsWith("- ") || line.startsWith("• ") -> Row {
                    androidx.compose.material3.Text("•  ", color = color, fontSize = baseSize)
                    androidx.compose.material3.Text(inlineMarkdown(line.drop(2)), color = color, fontSize = baseSize, lineHeight = baseSize * 1.5f)
                }
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                else -> androidx.compose.material3.Text(
                    inlineMarkdown(line), color = color, fontSize = baseSize, lineHeight = baseSize * 1.5f,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
