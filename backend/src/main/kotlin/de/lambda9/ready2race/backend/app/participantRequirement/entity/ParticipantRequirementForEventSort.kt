package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT_FOR_EVENT
import org.jooq.Field

enum class ParticipantRequirementForEventSort : Sortable {
    ID,
    NAME,
    ACTIVE;

    override fun toField(): Field<*> = when (this) {
        ID -> PARTICIPANT_REQUIREMENT_FOR_EVENT.ID
        NAME -> PARTICIPANT_REQUIREMENT_FOR_EVENT.NAME
        ACTIVE -> PARTICIPANT_REQUIREMENT_FOR_EVENT.ACTIVE
    }
}