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

    override fun toFields(): List<Field<*>> = when (this) {
        ID ->  listOf(COMPETITION_TEMPLATE_VIEW.ID)
        NAME -> listOf(COMPETITION_TEMPLATE_VIEW.NAME)
        SHORT_NAME -> listOf(COMPETITION_TEMPLATE_VIEW.SHORT_NAME)
        IDENTIFIER -> listOf(COMPETITION_TEMPLATE_VIEW.IDENTIFIER)
        COMPETITION_CATEGORY -> listOf(COMPETITION_TEMPLATE_VIEW.CATEGORY_NAME)
    }
}