package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT_FOR_EVENT
import org.jooq.Field

enum class ParticipantRequirementForEventSort : Sortable {
    ID,
    NAME,
    ACTIVE;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(PARTICIPANT_REQUIREMENT_FOR_EVENT.ID)
        NAME -> listOf(PARTICIPANT_REQUIREMENT_FOR_EVENT.NAME)
        ACTIVE -> listOf(PARTICIPANT_REQUIREMENT_FOR_EVENT.ACTIVE)
    }
}