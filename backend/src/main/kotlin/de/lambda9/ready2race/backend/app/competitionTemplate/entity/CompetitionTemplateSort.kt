package de.lambda9.ready2race.backend.app.competitionTemplate.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_TEMPLATE_VIEW
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class CompetitionTemplateWithPropertiesSort : Sortable {
    ID,
    NAME,
    SHORT_NAME,
    IDENTIFIER,
    COMPETITION_CATEGORY;

    override fun toField(): Field<*> = when (this) {
        ID ->  COMPETITION_TEMPLATE_VIEW.ID
        NAME -> COMPETITION_TEMPLATE_VIEW.NAME
        SHORT_NAME -> COMPETITION_TEMPLATE_VIEW.SHORT_NAME
        IDENTIFIER -> COMPETITION_TEMPLATE_VIEW.IDENTIFIER
        COMPETITION_CATEGORY -> COMPETITION_TEMPLATE_VIEW.CATEGORY_NAME
    }
}