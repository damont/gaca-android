package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.GacaApi

/** api-contract.md §8: never cached by gaca-api — every call is live against that crew
 * member's own Hermes gateway, so it can never show a skill Hermes no longer has
 * installed. This repo doesn't cache either, for the same reason. */
class SkillsRepository(private val api: GacaApi) {
    /** Most used first (`sort=popular`), so the composer's sheet can show the top few without re-sorting. */
    suspend fun getSkills(crewName: String): List<Skill> = api.getSkills(crewName, sort = "popular").map { it.toDomain() }
}
