package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.AlertDto
import dev.monts.gaca.data.remote.MessageDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app decides nothing about an alert: the API sends `actions` and `workflow` and the app shows them.
 * These parse an alert the way gaca-api sends it, so a change to that contract fails here first.
 */
class AlertWorkflowTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val partway = """
    {"id":"a1","user_id":"u","created_at":"2026-11-17T12:30:00Z","source":"email-calendar-suggestions","crew":"default",
     "kind":"calendar.suggestions","severity":"info","title":"3 things","status":"new","effective_status":"new","open":true,
     "status_changed_at":"2026-11-17T12:30:00Z","session_id":"s1","runs":[],"draft":null,
     "actions":[
       {"index":0,"kind":"calendar.add","label":"Add Soccer","detail":"Thu Nov 19 · 5:30 PM","cta":"Add","prominent":false,"state":"done","can_run":false,
        "result":{"summary":"Added Soccer.","links":[{"url":"https://cal.example.com/w","label":"cal.example.com"},{"url":"javascript:alert(1)"}]}},
       {"index":1,"kind":"calendar.add","label":"Add Dismissal","detail":"Fri Nov 20 · all day","cta":"Add","prominent":false,"state":"idle","can_run":true},
       {"index":3,"kind":"calendar.add","label":"Add the other 2","cta":"Add","prominent":true,"state":"idle","can_run":true}],
     "workflow":{"phase":"open","phase_label":null,"actions_heading":"Hermes can add these","progress_label":"1 of 3 added",
       "chat":{"hint":"Hermes already has this alert","prominent":false},"close_heading":"Or close it yourself",
       "close_options":[{"reason":"done_by_me","label":"I'll handle it","primary":true},{"reason":"noted","label":"Good to know"},{"reason":"not_useful","label":"Not useful"}],
       "close_hint":"The alert closes by itself once all of them are done.","closed_note":null}}
    """

    @Test fun `actions and the layout come through exactly as the API decided them`() {
        val alert = json.decodeFromString<AlertDto>(partway).toDomain()
        assertEquals(listOf("done", "idle", "idle"), alert.actions.map { it.state })
        assertEquals(listOf(false, true, true), alert.actions.map { it.canRun })
        assertEquals("Add the other 2", alert.actions[2].label)          // the label is the API's, not the app's
        assertTrue(alert.actions[2].prominent)
        assertEquals("1 of 3 added", alert.workflow.progressLabel)
        assertEquals(listOf("done_by_me", "noted", "not_useful"), alert.workflow.closeOptions.map { it.reason })
        assertTrue(alert.workflow.closeOptions[0].primary)
        assertEquals("new", alert.status)
    }

    @Test fun `only web addresses in a result become tappable`() {
        val links = json.decodeFromString<AlertDto>(partway).toDomain().actions[0].result!!.links
        assertEquals(listOf("https://cal.example.com/w"), links.map { it.url })
    }

    @Test fun `a draft carries the links the API built for opening and copying it`() {
        val alert = json.decodeFromString<AlertDto>(
            partway.replace(
                "\"draft\":null",
                "\"draft\":{\"to\":\"Pat <pat@x.com>\",\"subject\":\"Re: Lease\",\"body\":\"Hi Pat\",\"copy_text\":\"Subject: Re: Lease\\n\\nHi Pat\",\"mailto\":\"mailto:pat@x.com?subject=Re%3A%20Lease&body=Hi%20Pat\"}",
            ),
        ).toDomain()
        val draft = alert.draft!!
        assertEquals("mailto:pat@x.com?subject=Re%3A%20Lease&body=Hi%20Pat", draft.mailto)
        assertEquals("Subject: Re: Lease\n\nHi Pat", draft.copyText)
    }

    @Test fun `an older server with none of the new fields still parses, as an open alert with nothing to tap`() {
        val alert = json.decodeFromString<AlertDto>(
            """{"id":"a","user_id":"u","created_at":"2026-11-17T12:30:00Z","source":"s","kind":"k","severity":"warn","title":"t",
                "status":"new","effective_status":"new","status_changed_at":"2026-11-17T12:30:00Z"}""",
        ).toDomain()
        assertTrue(alert.actions.isEmpty())
        assertEquals("open", alert.workflow.phase)
        assertTrue(alert.workflow.closeOptions.isEmpty())
        assertNull(alert.draft)
    }

    @Test fun `a chat reply with a draft arrives as the words before it plus the draft`() {
        val message = json.decodeFromString<MessageDto>(
            """{"id":"m","session_id":"s","role":"assistant","content":"Here is a shorter one:\nTo: P\nSubject: S\n\nBody","created_at":"2026-11-17T12:30:00Z",
                "display_content":"Here is a shorter one:","draft":{"to":"P","subject":"S","body":"Body","copy_text":"Subject: S\n\nBody","mailto":"mailto:?subject=S&body=Body"}}""",
        ).toDomain()
        assertEquals("Here is a shorter one:", message.displayContent)
        assertEquals("Body", message.draft!!.body)
        assertFalse(message.pending)
    }
}
