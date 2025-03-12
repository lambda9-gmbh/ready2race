package de.lambda9.ready2race.backend.app.competition.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_VIEW
import de.lambda9.ready2race.backend.calls.pagination.Sortable
import org.jooq.Field

enum class CompetitionWithPropertiesSort : Sortable {
    ID,
    EVENT,
    NAME,
    SHORT_NAME,
    IDENTIFIER,
    COMPETITION_CATEGORY;


    override fun toField(): Field<*> = when (this) {
        ID ->  COMPETITION_VIEW.ID
        EVENT -> COMPETITION_VIEW.EVENT
        NAME -> COMPETITION_VIEW.NAME
        SHORT_NAME -> COMPETITION_VIEW.SHORT_NAME
        IDENTIFIER -> COMPETITION_VIEW.IDENTIFIER
        COMPETITION_CATEGORY -> COMPETITION_VIEW.CATEGORY_NAME
    }
}