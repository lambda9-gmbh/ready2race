package de.lambda9.ready2race.backend.app.namedParticipant.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class NamedParticipantSort : Sortable {
    ID,
    NAME;

    override fun toField(): Field<*> = when(this) {
        ID -> NAMED_PARTICIPANT.ID
        NAME -> NAMED_PARTICIPANT.NAME
    }
}