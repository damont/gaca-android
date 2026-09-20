package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.SkillDto

/** What a crew member can actually do — api-contract.md §8. `category` is null for an
 * uncategorized skill; render that as its own group rather than dropping it. */
data class Skill(val name: String, val description: String, val category: String?)

fun SkillDto.toDomain(): Skill = Skill(name, description, category)
