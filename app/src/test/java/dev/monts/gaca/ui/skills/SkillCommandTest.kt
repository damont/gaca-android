package dev.monts.gaca.ui.skills

import dev.monts.gaca.data.repo.Skill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkillCommandTest {
    private val lists = Skill("gaca-lists", "Add to or read your lists", "Organize", useCount = 9)
    private val calendar = Skill("google-calendar", "Read and edit your calendar", "Calendar")
    private val suggestions = Skill("calendar-suggestions", "Turns emails into proposed events", "Calendar")
    private val all = listOf(lists, calendar, suggestions)

    @Test fun `a bare slash word is a query, anything else is not`() {
        assertEquals("", slashQuery("/"))
        assertEquals("cal", slashQuery("/cal"))
        assertNull(slashQuery("/cal now"))
        assertNull(slashQuery("hello"))
    }

    @Test fun `names that start with the query come before ones that only contain it`() {
        assertEquals(listOf(suggestions, calendar), matchSkills(all, "cal").let { listOf(it[0], it[1]) })
        assertEquals(all, matchSkills(all, ""))
        assertEquals(listOf(lists), matchSkills(all, "LISTS"))
    }

    @Test fun `description matches count`() {
        assertEquals(listOf(suggestions), matchSkills(all, "emails"))
    }

    @Test fun `picking a skill puts its command first and keeps the rest`() {
        assertEquals("/gaca-lists ", withSkillCommand("", lists))
        assertEquals("/gaca-lists add milk", withSkillCommand("add milk", lists))
        assertEquals("/gaca-lists ", withSkillCommand("/cal", lists))
        assertEquals("/gaca-lists add milk", withSkillCommand("/cal add milk", lists))
    }

    @Test fun `a leading skill command is sent as the plain request`() {
        assertEquals("Use the gaca-lists skill: add milk", expandSkillCommand("/gaca-lists add milk", all))
        assertEquals("Use the gaca-lists skill.", expandSkillCommand("/gaca-lists", all))
        assertEquals("Use the gaca-lists skill: add milk", expandSkillCommand("/GACA-LISTS   add milk", all))
    }

    @Test fun `text that isn't a known skill command goes through untouched`() {
        assertEquals("/nonsense add milk", expandSkillCommand("/nonsense add milk", all))
        assertEquals("what is /gaca-lists", expandSkillCommand("what is /gaca-lists", all))
    }

    @Test fun `skills group by category with the uncategorized last`() {
        val loose = Skill("misc", "", null)
        val grouped = groupedByCategory(listOf(loose, lists, calendar, suggestions))
        assertEquals(listOf("Organize", "Calendar", "Other"), grouped.map { it.first })
        assertEquals(listOf(calendar, suggestions), grouped[1].second)
    }
}
