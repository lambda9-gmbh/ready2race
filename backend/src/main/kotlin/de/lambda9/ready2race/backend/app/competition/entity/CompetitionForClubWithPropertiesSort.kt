package de.lambda9.ready2race.backend.app.competition.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_FOR_CLUB_VIEW
import org.jooq.Field

//todo @Cleanup: unused enum?

enum class CompetitionForClubWithPropertiesSort : CompetitionSortable {
    ID,
    EVENT,
    NAME,
    SHORT_NAME,
    IDENTIFIER,
    COMPETITION_CATEGORY;


    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(COMPETITION_FOR_CLUB_VIEW.ID)
        EVENT -> listOf(COMPETITION_FOR_CLUB_VIEW.EVENT)
        NAME -> listOf(COMPETITION_FOR_CLUB_VIEW.NAME)
        SHORT_NAME -> listOf(COMPETITION_FOR_CLUB_VIEW.SHORT_NAME)
        IDENTIFIER -> listOf(COMPETITION_FOR_CLUB_VIEW.IDENTIFIER)
        COMPETITION_CATEGORY -> listOf(COMPETITION_FOR_CLUB_VIEW.CATEGORY_NAME)
    }
}