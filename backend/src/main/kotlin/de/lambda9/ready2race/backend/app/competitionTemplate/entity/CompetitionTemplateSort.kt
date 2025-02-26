package de.lambda9.ready2race.backend.app.competitionTemplate.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class CompetitionTemplateWithPropertiesSort : Sortable {
    ID,
    NAME,
    SHORT_NAME,
    IDENTIFIER,
    COMPETITION_CATEGORY;

    override fun toField(): Field<*> = when (this) {
        ID ->  COMPETITION_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.ID
        NAME -> COMPETITION_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.NAME
        SHORT_NAME -> COMPETITION_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.SHORT_NAME
        IDENTIFIER -> COMPETITION_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.IDENTIFIER
        COMPETITION_CATEGORY -> COMPETITION_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS.CATEGORY_NAME
    }
}