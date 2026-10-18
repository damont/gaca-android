package dev.monts.gaca.ui.skills

import dev.monts.gaca.data.repo.Skill

/** A message that is only a `/word` so far — the composer shows the skill picker for it. */
private val SLASH_QUERY = Regex("^/(\\S*)$")

/** What the person has typed after the leading "/", or null when the text isn't a bare `/word`. */
fun slashQuery(text: String): String? = SLASH_QUERY.matchEntire(text)?.groupValues?.get(1)

/** The skills the picker lists for [query]: names starting with it first, then names or descriptions containing it. */
fun matchSkills(skills: List<Skill>, query: String): List<Skill> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return skills
    val starts = skills.filter { it.name.lowercase().startsWith(q) }
    val contains = skills.filter { s ->
        s !in starts && (s.name.lowercase().contains(q) || s.description.lowercase().contains(q))
    }
    return starts + contains
}

/** Puts `/name ` at the start of [text], replacing a `/word` already there; the rest of the message stays. */
fun withSkillCommand(text: String, skill: Skill): String {
    val rest = if (text.startsWith("/")) text.substringAfter(' ', missingDelimiterValue = "") else text
    return "/${skill.name} ${rest.trimStart()}"
}

/**
 * What actually gets sent: a leading `/skill-name rest` becomes the same plain request a tapped skill
 * always produced ("Use the skill-name skill: rest"), so the crew member reads it the way it always has.
 * Anything that doesn't start with a `/` and a skill this crew member has goes through untouched.
 */
fun expandSkillCommand(text: String, skills: List<Skill>): String {
    if (!text.startsWith("/")) return text
    val name = text.drop(1).takeWhile { !it.isWhitespace() }
    val skill = skills.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return text
    val rest = text.drop(1 + name.length).trim()
    return if (rest.isEmpty()) "Use the ${skill.name} skill." else "Use the ${skill.name} skill: $rest"
}
