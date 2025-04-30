package de.lambda9.ready2race.backend.app.competition.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PUBLIC_VIEW
import org.jooq.Field

enum class CompetitionPublicSort : CompetitionSortable {
    ID,
    EVENT,
    NAME,
    SHORT_NAME,
    IDENTIFIER,
    COMPETITION_CATEGORY;


    override fun toFields(): List<Field<*>> = when (this) {
        ID ->  listOf(COMPETITION_PUBLIC_VIEW.ID)
        EVENT -> listOf(COMPETITION_PUBLIC_VIEW.EVENT)
        NAME -> listOf(COMPETITION_PUBLIC_VIEW.NAME)
        SHORT_NAME -> listOf(COMPETITION_PUBLIC_VIEW.SHORT_NAME)
        IDENTIFIER -> listOf(COMPETITION_PUBLIC_VIEW.IDENTIFIER)
        COMPETITION_CATEGORY -> listOf(COMPETITION_PUBLIC_VIEW.CATEGORY_NAME)
    }
}