package dev.monts.gaca.ui.settings

import dev.monts.gaca.data.repo.UnlinkBlock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLinkTextTest {
    @Test fun `the unlink warning says how many pages would be cut off, in plain words`() {
        val text = blockedText(UnlinkBlock(owned = 1, shared = 2, inProgress = 0))
        assertTrue(text.startsWith("In the cloud: 1 of your own, 2 shared with you."))
        assertFalse(text.contains("still running"))
    }

    @Test fun `a move still running is mentioned, and zero counts still give a sentence`() {
        assertTrue(blockedText(UnlinkBlock(0, 0, 1)).contains("1 move still running"))
        assertTrue(blockedText(UnlinkBlock(0, 0, 2)).contains("2 moves still running"))
        assertTrue(blockedText(UnlinkBlock(0, 0, 0)).startsWith("Some pages live in the cloud"))
    }
}
