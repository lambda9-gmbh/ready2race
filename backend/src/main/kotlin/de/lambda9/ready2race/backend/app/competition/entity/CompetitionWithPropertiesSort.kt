package de.lambda9.ready2race.backend.app.competition.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_VIEW
import org.jooq.Field

enum class CompetitionWithPropertiesSort : CompetitionSortable {
    ID,
    EVENT,
    NAME,
    SHORT_NAME,
    IDENTIFIER,
    COMPETITION_CATEGORY;


    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(COMPETITION_VIEW.ID)
        EVENT -> listOf(COMPETITION_VIEW.EVENT)
        NAME -> listOf(COMPETITION_VIEW.NAME)
        SHORT_NAME -> listOf(COMPETITION_VIEW.SHORT_NAME)
        IDENTIFIER -> listOf(COMPETITION_VIEW.IDENTIFIER_PREFIX, COMPETITION_VIEW.IDENTIFIER_SUFFIX)
        COMPETITION_CATEGORY -> listOf(COMPETITION_VIEW.CATEGORY_NAME)
    }
}