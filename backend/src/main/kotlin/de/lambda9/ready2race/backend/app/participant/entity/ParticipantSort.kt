package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import org.jooq.Field

enum class ParticipantSort : Sortable {
    ID,
    FIRSTNAME,
    LASTNAME,
    EXTERNAL_CLUB_NAME,
    CREATED_AT;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(PARTICIPANT.ID)
        FIRSTNAME -> listOf(PARTICIPANT.FIRSTNAME)
        LASTNAME -> listOf(PARTICIPANT.LASTNAME)
        EXTERNAL_CLUB_NAME -> listOf(PARTICIPANT.EXTERNAL_CLUB_NAME)
        CREATED_AT -> listOf(PARTICIPANT.CREATED_AT)
    }
}