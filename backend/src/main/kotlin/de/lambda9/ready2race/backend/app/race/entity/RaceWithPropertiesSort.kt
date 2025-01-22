package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS
import de.lambda9.ready2race.backend.http.Sortable
import org.jooq.Field

enum class RaceWithPropertiesSort : Sortable {
    ID,
    EVENT,
    NAME,
    SHORT_NAME,
    IDENTIFIER,
    RACE_CATEGORY;


    override fun toField(): Field<*> = when (this) {
        ID ->  RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID
        EVENT -> RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.EVENT
        NAME -> RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAME
        SHORT_NAME -> RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.SHORT_NAME
        IDENTIFIER -> RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.IDENTIFIER
        RACE_CATEGORY -> RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.CATEGORY_NAME
    }
}