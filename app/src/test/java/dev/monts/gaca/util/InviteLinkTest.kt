package dev.monts.gaca.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinkTest {
    private val token = "AbC123_-xyz789AbC123_-xyz"

    @Test fun `a web invite link gives its token, whatever the host, with or without extras`() {
        assertEquals(token, parseInviteToken("https://cloud.example/join/$token"))
        assertEquals(token, parseInviteToken("  https://cloud.example/join/$token\n"))
        assertEquals(token, parseInviteToken("https://cloud.example/join/$token?utm=1#top"))
        assertEquals(token, parseInviteToken("https://other.example:8443/join/$token/"))
        assertEquals(token, parseInviteToken("http://localhost/join/$token"))
    }

    @Test fun `the app's own link and a bare token work too`() {
        assertEquals(token, parseInviteToken("gaca://join/$token"))
        assertEquals(token, parseInviteToken(token))
    }

    @Test fun `anything else is not an invite`() {
        assertNull(parseInviteToken(null))
        assertNull(parseInviteToken(""))
        assertNull(parseInviteToken("   "))
        assertNull(parseInviteToken("https://cloud.example/"))
        assertNull(parseInviteToken("https://cloud.example/join"))
        assertNull(parseInviteToken("https://cloud.example/gallery/$token"))
        assertNull(parseInviteToken("gaca://artifact/$token"))
        assertNull(parseInviteToken("gaca://join"))
        assertNull(parseInviteToken("javascript://x/join/$token"))
        assertNull(parseInviteToken("ftp://x/join/$token"))
        assertNull(parseInviteToken("short"))
        assertNull(parseInviteToken("two words $token"))
        assertNull(parseInviteToken("https://cloud.example/join/has space"))
        assertNull(parseInviteToken("https://cloud.example/join/bad!chars!here"))
    }

    @Test fun `text with a link inside a sentence is refused, so only a clean paste is acted on`() {
        assertNull(parseInviteToken("Join me: https://cloud.example/join/$token"))
    }
}
