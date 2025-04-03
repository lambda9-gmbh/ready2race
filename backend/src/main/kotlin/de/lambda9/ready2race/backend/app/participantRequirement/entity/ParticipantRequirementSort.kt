package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT
import org.jooq.Field

enum class ParticipantRequirementSort : Sortable {
    ID,
    NAME;

    override fun toField(): Field<*> = when (this) {
        ID -> PARTICIPANT_REQUIREMENT.ID
        NAME -> PARTICIPANT_REQUIREMENT.NAME
    }
}