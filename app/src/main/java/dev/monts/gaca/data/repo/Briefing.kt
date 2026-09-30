package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.BriefingDto
import dev.monts.gaca.data.remote.BriefingItemDto
import dev.monts.gaca.data.remote.BriefingSectionDto
import dev.monts.gaca.data.remote.GacaApi
import java.time.LocalDate

/** One day's digest, assembled server-side from sections that Hermes posts. Display only —
 * ordering, labeling and content all come from the API. */
data class Briefing(
    val date: String,
    val summary: String?,
    val sections: List<BriefingSection>,
) {
    val isEmpty: Boolean get() = summary.isNullOrBlank() && sections.isEmpty()
}

data class BriefingSection(
    val key: String,
    val title: String,
    /** Display hint: general | calendar | weather | news | sports | tasks. */
    val kind: String,
    val headline: String?,
    val items: List<BriefingItem>,
)

data class BriefingItem(
    val title: String,
    val body: String?,
    val time: String?,
    val location: String?,
    val link: String?,
    /** high | medium | low */
    val priority: String,
)

fun BriefingItemDto.toDomain() = BriefingItem(title, body, time, location, link, priority)
fun BriefingSectionDto.toDomain() = BriefingSection(key, title, kind, headline, items.map { it.toDomain() })
fun BriefingDto.toDomain() = Briefing(date, summary, sections.map { it.toDomain() })

class BriefingRepository(private val api: GacaApi) {
    /** The briefing for [date], in the device's local calendar — the API defaults to UTC, which
     * would show tomorrow's (empty) briefing every evening. */
    suspend fun getBriefing(date: LocalDate): Briefing = api.getBriefing(date.toString()).toDomain()
}
