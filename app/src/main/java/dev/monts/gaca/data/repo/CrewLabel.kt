package dev.monts.gaca.data.repo

/** What to call a crew member next to old chats and alerts: a removed one is marked, never silently shown as current. */
fun crewLabel(name: String, removed: Boolean): String = if (removed) "$name (removed)" else name

/** The words on the "remove a crew member" confirmation. When gaca-api can reach the Hermes box
 * ([deletesProfile], from [CrewConfig.canCreate]) removing really deletes the profile there. */
data class RemoveCrewPrompt(val title: String, val body: String, val confirm: String)

fun removeCrewPrompt(name: String, deletesProfile: Boolean): RemoveCrewPrompt =
    if (deletesProfile) {
        RemoveCrewPrompt(
            title = "Delete $name?",
            body = "This permanently deletes the profile on your Hermes box, including its memory, chats with the agent, " +
                "skills and scheduled jobs. Past chats and alerts stay as read-only history.",
            confirm = "Delete",
        )
    } else {
        RemoveCrewPrompt(
            title = "Remove $name from the app?",
            body = "This does not delete anything on your Hermes box.",
            confirm = "Remove",
        )
    }
