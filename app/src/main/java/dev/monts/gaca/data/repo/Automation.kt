package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.AutomationDto
import dev.monts.gaca.data.remote.AutomationListDto
import dev.monts.gaca.data.remote.AutomationRunDto
import dev.monts.gaca.data.remote.RunMarkDto
import dev.monts.gaca.util.parseInstant
import java.time.Instant

/** How one run ended. An unknown word from a newer server reads as [OK] rather than as a failure. */
enum class RunStatus { OK, FAILED, RUNNING }

/** The job's state as the API words it: paused jobs were switched off by the crew member, not the app. */
enum class AutomationStatus { OK, FAILED, RUNNING, PAUSED, DONE, NEVER_RUN }

data class RunMark(val id: String?, val status: RunStatus, val at: Instant?)

/** Something a crew member does on a schedule (api: GET /automations). */
data class Automation(
    val id: String,
    val name: String,
    val crew: String,
    /** Plain English, e.g. "Every day at 7:30 AM". */
    val schedule: String,
    val cronExpr: String?,
    val status: AutomationStatus,
    val lastStatus: RunStatus?,
    val lastRun: Instant?,
    val nextRun: Instant?,
    /** At most 10, oldest first, so the newest sits on the right. */
    val recent: List<RunMark>,
) {
    val needsAttention: Boolean get() = status == AutomationStatus.FAILED
}

data class AutomationList(val automations: List<Automation>, val unavailable: List<String>)

data class AutomationRun(
    val id: String,
    val status: RunStatus,
    val startedAt: Instant?,
    val durationSeconds: Double?,
    val summary: String?,
    val error: String?,
)

private fun runStatus(word: String?): RunStatus = when (word) {
    "failed" -> RunStatus.FAILED
    "running" -> RunStatus.RUNNING
    else -> RunStatus.OK
}

private fun automationStatus(word: String): AutomationStatus = when (word) {
    "failed" -> AutomationStatus.FAILED
    "running" -> AutomationStatus.RUNNING
    "paused" -> AutomationStatus.PAUSED
    "done" -> AutomationStatus.DONE
    "never_run" -> AutomationStatus.NEVER_RUN
    else -> AutomationStatus.OK
}

private fun RunMarkDto.toDomain() = RunMark(id, runStatus(status), parseInstant(at))

fun AutomationDto.toDomain() = Automation(
    id = id,
    name = name,
    crew = crew,
    schedule = schedule,
    cronExpr = cronExpr,
    status = automationStatus(status),
    lastStatus = lastStatus?.let(::runStatus),
    lastRun = parseInstant(lastRun),
    nextRun = parseInstant(nextRun),
    recent = recent.map { it.toDomain() },
)

fun AutomationListDto.toDomain() = AutomationList(automations.map { it.toDomain() }, unavailable)

fun AutomationRunDto.toDomain() = AutomationRun(
    id = id,
    status = runStatus(status),
    startedAt = parseInstant(startedAt),
    durationSeconds = durationSeconds,
    summary = summary?.takeIf { it.isNotBlank() },
    error = error?.takeIf { it.isNotBlank() },
)
