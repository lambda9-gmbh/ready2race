package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_VIEW
import org.jooq.Field

enum class ParticipantSort : Sortable {
    ID,
    FIRSTNAME,
    LASTNAME,
    EXTERNAL,
    EXTERNAL_CLUB_NAME,
    CREATED_AT;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(PARTICIPANT_VIEW.ID)
        FIRSTNAME -> listOf(PARTICIPANT_VIEW.FIRSTNAME)
        LASTNAME -> listOf(PARTICIPANT_VIEW.LASTNAME)
        EXTERNAL -> listOf(PARTICIPANT_VIEW.EXTERNAL)
        EXTERNAL_CLUB_NAME -> listOf(PARTICIPANT_VIEW.EXTERNAL_CLUB_NAME)
        CREATED_AT -> listOf(PARTICIPANT_VIEW.CREATED_AT)
    }
}