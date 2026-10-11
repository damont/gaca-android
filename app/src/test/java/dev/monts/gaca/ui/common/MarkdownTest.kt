package dev.monts.gaca.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    @Test
    fun parsesTheBlocksAgentsWrite() {
        val blocks = parseMarkdown("# Title\n\nHello **world**\nsecond line\n\n- one\n  - nested\n2. two\n\n```kotlin\nval x = 1\n```\n---")
        assertEquals(
            listOf(
                MdBlock.Heading(1, "Title"),
                MdBlock.Paragraph("Hello **world**\nsecond line"),
                MdBlock.ListItem("•", "one", 0),
                MdBlock.ListItem("•", "nested", 1),
                MdBlock.ListItem("2.", "two", 0),
                MdBlock.Code("val x = 1"),
                MdBlock.Rule,
            ),
            blocks,
        )
    }

    @Test
    fun unterminatedFenceRunsToTheEnd() {
        assertEquals(listOf(MdBlock.Code("a\nb")), parseMarkdown("```\na\nb"))
    }
}
