package de.lambda9.ready2race.backend.app.raceTemplate.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class RaceTemplateWithPropertiesSort : Sortable {
    ID,
    NAME,
    SHORT_NAME,
    IDENTIFIER,
    RACE_CATEGORY;

    override fun toField(): Field<*> = when (this) {
        ID ->  RACE_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID
        NAME -> RACE_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAME
        SHORT_NAME -> RACE_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.SHORT_NAME
        IDENTIFIER -> RACE_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.IDENTIFIER
        RACE_CATEGORY -> RACE_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.CATEGORY_NAME
    }
}