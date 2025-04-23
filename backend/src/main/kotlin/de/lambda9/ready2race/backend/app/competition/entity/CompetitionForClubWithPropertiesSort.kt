package de.lambda9.ready2race.backend.app.competition.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_FOR_CLUB_VIEW
import org.jooq.Field

enum class CompetitionForClubWithPropertiesSort : CompetitionSortable {
    ID,
    EVENT,
    NAME,
    SHORT_NAME,
    IDENTIFIER,
    COMPETITION_CATEGORY;


    override fun toField(): Field<*> = when (this) {
        ID -> COMPETITION_FOR_CLUB_VIEW.ID
        EVENT -> COMPETITION_FOR_CLUB_VIEW.EVENT
        NAME -> COMPETITION_FOR_CLUB_VIEW.NAME
        SHORT_NAME -> COMPETITION_FOR_CLUB_VIEW.SHORT_NAME
        IDENTIFIER -> COMPETITION_FOR_CLUB_VIEW.IDENTIFIER
        COMPETITION_CATEGORY -> COMPETITION_FOR_CLUB_VIEW.CATEGORY_NAME
    }
}