package dev.monts.gaca.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderTest {
    private fun message(id: String, role: String, at: String) = ConversationMessage(id, role, "", at)

    private fun reminder(id: String, created: String, due: String? = "2026-11-23T15:00:00Z", kind: String = "chat", session: String? = null, state: String = "pending") =
        Reminder(id, "Call the dentist", due?.let(Instant::parse), state, session, null, kind, Instant.parse(created))

    private val chat = listOf(
        message("u1", "user", "2026-11-22T10:00:00Z"),
        message("a1", "assistant", "2026-11-22T10:00:20Z"),
        message("u2", "user", "2026-11-22T10:05:00Z"),
        message("a2", "assistant", "2026-11-22T10:05:30Z"),
    )

    @Test fun `a reminder belongs to the reply whose turn it was made in`() {
        val result = remindersByReply(chat, listOf(reminder("r1", "2026-11-22T10:00:10Z"), reminder("r2", "2026-11-22T10:05:12Z")), "s1")
        assertEquals(listOf("r1"), result["a1"]?.map { it.id })
        assertEquals(listOf("r2"), result["a2"]?.map { it.id })
    }

    @Test fun `one made between turns, or long before, matches nothing`() {
        assertEquals(emptyMap<String, List<Reminder>>(), remindersByReply(chat, listOf(reminder("r1", "2026-11-22T10:03:00Z"), reminder("r2", "2026-11-21T09:00:00Z")), "s1"))
    }

    @Test fun `a reminder from another chat or from an alert is never matched`() {
        val result = remindersByReply(chat, listOf(reminder("r1", "2026-11-22T10:00:10Z", session = "other"), reminder("r2", "2026-11-22T10:00:10Z", kind = "alert")), "s1")
        assertEquals(emptyMap<String, List<Reminder>>(), result)
    }

    @Test fun `a reminder that names this chat is matched, one that names none is matched by time`() {
        val result = remindersByReply(chat, listOf(reminder("r1", "2026-11-22T10:00:10Z", session = "s1"), reminder("r2", "2026-11-22T10:00:11Z")), "s1")
        assertEquals(listOf("r1", "r2"), result["a1"]?.map { it.id })
    }

    @Test fun `a later note has no time label and says so`() {
        assertNull(reminder("n", "2026-11-22T10:00:10Z", due = null).whenLabel())
        assert(reminder("n", "2026-11-22T10:00:10Z", due = null).isNote)
    }

    @Test fun `the time label says today and tomorrow in plain words`() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-11-22T08:00:00Z")
        assert(reminder("a", "2026-11-22T07:00:00Z", due = "2026-11-22T18:00:00Z").whenLabel(now, zone)!!.startsWith("Today"))
        assert(reminder("b", "2026-11-22T07:00:00Z", due = "2026-11-23T10:00:00Z").whenLabel(now, zone)!!.startsWith("Tomorrow"))
    }
}
