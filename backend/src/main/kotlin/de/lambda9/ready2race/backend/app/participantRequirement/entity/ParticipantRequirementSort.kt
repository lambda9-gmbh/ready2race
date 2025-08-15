package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT
import org.jooq.Field

enum class ParticipantRequirementSort : Sortable {
    ID,
    NAME;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(PARTICIPANT_REQUIREMENT.ID)
        NAME -> listOf(PARTICIPANT_REQUIREMENT.NAME)
    }
}