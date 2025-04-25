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


    override fun toField(): Field<*> = when (this) {
        ID ->  COMPETITION_PUBLIC_VIEW.ID
        EVENT -> COMPETITION_PUBLIC_VIEW.EVENT
        NAME -> COMPETITION_PUBLIC_VIEW.NAME
        SHORT_NAME -> COMPETITION_PUBLIC_VIEW.SHORT_NAME
        IDENTIFIER -> COMPETITION_PUBLIC_VIEW.IDENTIFIER
        COMPETITION_CATEGORY -> COMPETITION_PUBLIC_VIEW.CATEGORY_NAME
    }
}