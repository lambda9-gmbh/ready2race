package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import org.jooq.Field

enum class ParticipantSort : Sortable {
    ID,
    FIRSTNAME,
    LASTNAME,
    EXTERNAL,
    EXTERNAL_CLUB_NAME,
    CREATED_AT;

    override fun toField(): Field<*> = when (this) {
        ID -> PARTICIPANT.ID
        FIRSTNAME -> PARTICIPANT.FIRSTNAME
        LASTNAME -> PARTICIPANT.LASTNAME
        EXTERNAL -> PARTICIPANT.EXTERNAL
        EXTERNAL_CLUB_NAME -> PARTICIPANT.EXTERNAL_CLUB_NAME
        CREATED_AT -> PARTICIPANT.CREATED_AT
    }
}