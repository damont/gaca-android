package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.GacaApi

/** Never cached: every call asks the crew member's engine through gaca-api, so what shows is what is scheduled now. */
class AutomationsRepository(private val api: GacaApi) {
    suspend fun list(crew: String? = null): AutomationList = api.listAutomations(crew).toDomain()

    /** Newest first. */
    suspend fun runs(crew: String, id: String): List<AutomationRun> =
        api.getAutomationRuns(crew, id).map { it.toDomain() }

    /** Asks for a run; the engine starts it on its next tick (about a minute). Returns the job as it now stands. */
    suspend fun runNow(crew: String, id: String): Automation = api.runAutomation(crew, id).toDomain()
}
