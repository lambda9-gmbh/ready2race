package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_WITH_PROPERTIES
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
        ID -> RACE_WITH_PROPERTIES.ID
        EVENT -> RACE_WITH_PROPERTIES.EVENT
        NAME -> RACE_WITH_PROPERTIES.NAME
        SHORT_NAME -> RACE_WITH_PROPERTIES.SHORT_NAME
        IDENTIFIER -> RACE_WITH_PROPERTIES.IDENTIFIER
        RACE_CATEGORY -> RACE_WITH_PROPERTIES.RACE_CATEGORY
    }
}