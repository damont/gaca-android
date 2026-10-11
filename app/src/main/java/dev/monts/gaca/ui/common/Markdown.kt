package dev.monts.gaca.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monts.gaca.ui.theme.ActivityPill
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.TextPrimary

/** One block of a Markdown document. Deliberately small: what agents actually write. */
internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class ListItem(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data object Rule : MdBlock
}

private val headingRe = Regex("""^(#{1,6})\s+(.*)$""")
private val bulletRe = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val orderedRe = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val ruleRe = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")

internal fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isNotEmpty()) blocks += MdBlock.Paragraph(paragraph.joinToString("\n")).also { paragraph.clear() }
    }
    val lines = source.replace("\r\n", "\n").split("\n")
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flush()
                val code = mutableListOf<String>()
                i++
                // An unterminated fence (a reply still streaming in) just runs to the end.
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) code += lines[i++]
                blocks += MdBlock.Code(code.joinToString("\n"))
            }
            line.isBlank() -> flush()
            ruleRe.matches(line) -> { flush(); blocks += MdBlock.Rule }
            headingRe.matches(line) -> {
                flush()
                val (hashes, text) = headingRe.matchEntire(line)!!.destructured
                blocks += MdBlock.Heading(hashes.length, text.trim())
            }
            bulletRe.matches(line) -> {
                flush()
                val (lead, text) = bulletRe.matchEntire(line)!!.destructured
                blocks += MdBlock.ListItem("•", text, lead.length / 2)
            }
            orderedRe.matches(line) -> {
                flush()
                val (lead, n, text) = orderedRe.matchEntire(line)!!.destructured
                blocks += MdBlock.ListItem("$n.", text, lead.length / 2)
            }
            line.trimStart().startsWith(">") -> {
                flush()
                blocks += MdBlock.Quote(line.trimStart().removePrefix(">").trim())
            }
            else -> paragraph += line
        }
        i++
    }
    flush()
    return blocks
}

private val inlineRe = Regex("""\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`|\[([^\]]+)]\(([^)\s]+)\)|(?<![*\w])\*([^*\n]+?)\*(?!\w)|(?<![_\w])_([^_\n]+?)_(?!\w)""")

private fun inlineMarkdown(text: String, codeBackground: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (m in inlineRe.findAll(text)) {
        append(text.substring(cursor, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1]) }
            g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[2]) }
            g[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, background = codeBackground)) { append(g[3]) }
            g[4].isNotEmpty() -> {
                val url = g[5]
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = HeaderAccent, textDecoration = TextDecoration.Underline)))) { append(g[4]) }
                } else {
                    append(g[4])
                }
            }
            g[6].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]) }
            g[7].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[7]) }
        }
        cursor = m.range.last + 1
    }
    append(text.substring(cursor))
}

private val bodyStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = TextPrimary)

/** Renders agent replies: paragraphs, lists, bold/italic, inline code, code blocks, headings, links. */
@Composable
fun MarkdownText(source: String, modifier: Modifier = Modifier) {
    val blocks = remember(source) { parseMarkdown(source) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { MarkdownBlock(it) }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock) {
    when (block) {
        is MdBlock.Paragraph -> Text(inlineMarkdown(block.text, ActivityPill), style = bodyStyle)
        is MdBlock.Heading -> Text(
            inlineMarkdown(block.text, ActivityPill),
            style = bodyStyle.copy(
                fontWeight = FontWeight.Bold,
                fontSize = when (block.level) { 1 -> 22.sp; 2 -> 19.sp; else -> 17.sp },
                lineHeight = 26.sp,
            ),
            modifier = Modifier.padding(top = 4.dp),
        )
        is MdBlock.ListItem -> Row(Modifier.padding(start = (block.indent * 16).dp)) {
            Text(block.marker, style = bodyStyle, modifier = Modifier.width(24.dp))
            Text(inlineMarkdown(block.text, ActivityPill), style = bodyStyle, modifier = Modifier.weight(1f))
        }
        is MdBlock.Quote -> Row {
            androidx.compose.foundation.layout.Box(Modifier.width(3.dp).padding(vertical = 2.dp).background(ActivityPill))
            Text(inlineMarkdown(block.text, ActivityPill), style = bodyStyle.copy(fontStyle = FontStyle.Italic), modifier = Modifier.padding(start = 10.dp))
        }
        is MdBlock.Code -> Text(
            block.text,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = TextPrimary),
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ActivityPill)
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
        MdBlock.Rule -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(ActivityPill))
    }
}
