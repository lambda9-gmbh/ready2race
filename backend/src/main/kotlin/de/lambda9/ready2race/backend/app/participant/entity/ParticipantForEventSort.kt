package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_FOR_EVENT
import org.jooq.Field

enum class ParticipantForEventSort : Sortable {
    FIRSTNAME,
    LASTNAME,
    EXTERNAL_CLUB_NAME,
    CLUB_NAME;

    override fun toField(): Field<*> = when (this) {
        FIRSTNAME -> PARTICIPANT_FOR_EVENT.FIRSTNAME
        LASTNAME -> PARTICIPANT_FOR_EVENT.LASTNAME
        EXTERNAL_CLUB_NAME -> PARTICIPANT_FOR_EVENT.EXTERNAL_CLUB_NAME
        CLUB_NAME -> PARTICIPANT_FOR_EVENT.CLUB_NAME
    }
}