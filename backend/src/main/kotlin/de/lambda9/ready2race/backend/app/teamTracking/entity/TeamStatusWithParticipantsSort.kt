package de.lambda9.ready2race.backend.app.teamTracking.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.TEAM_STATUS_WITH_PARTICIPANTS
import org.jooq.Field

enum class TeamStatusWithParticipantsSort: Sortable {
    TEAM_NAME,
    CURRENT_STATUS,
    LAST_SCAN_AT;

    override fun toFields(): List<Field<*>> = when (this) {
        TEAM_NAME -> listOf(TEAM_STATUS_WITH_PARTICIPANTS.TEAM_NAME)
        CURRENT_STATUS -> listOf(TEAM_STATUS_WITH_PARTICIPANTS.CURRENT_STATUS)
        LAST_SCAN_AT -> listOf(TEAM_STATUS_WITH_PARTICIPANTS.LAST_SCAN_AT)
    }
}