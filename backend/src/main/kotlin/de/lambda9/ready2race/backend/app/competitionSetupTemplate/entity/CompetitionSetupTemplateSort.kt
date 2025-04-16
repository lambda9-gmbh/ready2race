package de.lambda9.ready2race.backend.app.competitionSetupTemplate.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_TEMPLATE
import org.jooq.Field

enum class CompetitionSetupTemplateSort : Sortable {
    ID,
    NAME;

    override fun toField(): Field<*> = when (this) {
        ID -> COMPETITION_SETUP_TEMPLATE.ID
        NAME -> COMPETITION_SETUP_TEMPLATE.NAME
    }
}