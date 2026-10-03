package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.CrewProfileDto
import dev.monts.gaca.data.remote.GacaApi

/** How a crew member is set up, as the API describes it — display only. Nothing here is
 * interpreted by the app: the schedule wording, ordering and groupings all come from the API. */
data class CrewProfile(
    val name: String,
    val color: String,
    val isDefault: Boolean,
    val online: Boolean,
    val engine: String?,
    val modelName: String?,
    val modelProvider: String?,
    val channels: List<ProfileChannel>,
    val skillCount: Int,
    val skillCategories: List<Pair<String, Int>>,
    val toolsets: List<ProfileToolset>,
    val jobs: List<ProfileJob>,
    /** What it is dedicated to doing — `null` when the API couldn't read it (different from a generic assistant). */
    val purpose: CrewPurpose?,
    /** Parts the API couldn't read ("scheduled jobs"), so we can say so instead of showing an empty list as fact. */
    val unavailable: List<String>,
)

/** [summary] is the API's one-line version; [instructions] the full standing instructions;
 * [isGeneric] means nothing specific was ever set up (a general assistant). */
data class CrewPurpose(val summary: String?, val instructions: String?, val isGeneric: Boolean)

data class ProfileChannel(val name: String, val connected: Boolean, val needsAttention: Boolean)
data class ProfileToolset(val label: String, val description: String)
data class ProfileJob(
    val name: String,
    val schedule: String,
    val enabled: Boolean,
    val nextRun: String?,
    val lastRun: String?,
    val lastStatus: String?,
    val usesModel: Boolean,
)

fun CrewProfileDto.toDomain() = CrewProfile(
    name = name,
    color = color,
    isDefault = isDefault,
    online = status == "online",
    engine = engine,
    modelName = model?.name,
    modelProvider = model?.provider,
    channels = channels.map { ProfileChannel(it.name, it.state == "connected", it.needsAttention) },
    skillCount = skillCount,
    skillCategories = skillCategories.map { it.name to it.count },
    toolsets = toolsets.map { ProfileToolset(it.label, it.description) },
    jobs = jobs.map { ProfileJob(it.name, it.schedule, it.enabled, it.nextRun, it.lastRun, it.lastStatus, it.kind == "agent") },
    purpose = purpose?.let { CrewPurpose(it.summary, it.instructions, it.isGeneric) },
    unavailable = unavailable,
)

class CrewProfileRepository(private val api: GacaApi) {
    suspend fun getProfile(name: String): CrewProfile = api.getCrewProfile(name).toDomain()
}
