package de.lambda9.ready2race.backend.app.competition.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class CompetitionWithPropertiesSort : Sortable {
    ID,
    EVENT,
    NAME,
    SHORT_NAME,
    IDENTIFIER,
    COMPETITION_CATEGORY;


    override fun toField(): Field<*> = when (this) {
        ID ->  COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID
        EVENT -> COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.EVENT
        NAME -> COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAME
        SHORT_NAME -> COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.SHORT_NAME
        IDENTIFIER -> COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.IDENTIFIER
        COMPETITION_CATEGORY -> COMPETITION_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.CATEGORY_NAME
    }
}