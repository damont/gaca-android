package dev.monts.gaca.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    private fun n(s: String, http: Boolean = false) = normalizeServerUrl(s, http)

    @Test fun bareHostMeansHttps() = assertEquals("https://gaca.example.com/api/", n("gaca.example.com"))
    @Test fun trailingSlashAndApiAreNormalised() {
        assertEquals("https://gaca.example.com/api/", n("https://gaca.example.com/"))
        assertEquals("https://gaca.example.com/api/", n("https://gaca.example.com/api"))
        assertEquals("https://gaca.example.com/api/", n(" https://gaca.example.com/api/ "))
    }
    @Test fun subpathIsKept() = assertEquals("https://example.com/gaca/api/", n("example.com/gaca"))
    @Test fun portIsKept() = assertEquals("https://example.com:8443/api/", n("example.com:8443"))
    @Test fun httpOnlyWhenAllowed() {
        assertNull(n("http://192.168.1.5:41700"))
        assertEquals("http://192.168.1.5:41700/api/", n("http://192.168.1.5:41700", http = true))
    }
    @Test fun junkIsRejected() {
        assertNull(n(""))
        assertNull(n("   "))
        assertNull(n("not a url"))
        assertNull(n("ftp://example.com"))
        assertNull(n("https://user:pw@example.com"))
        assertNull(n("https://example.com/?x=1"))
        assertNull(n("javascript:alert(1)"))
    }
}
