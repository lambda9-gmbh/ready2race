package de.lambda9.ready2race.backend.app.results.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_HAVING_RESULTS
import org.jooq.Field

enum class CompetitionHavingResultsSort : Sortable {
    IDENTIFIER,
    NAME,
    SHORT_NAME,
    CATEGORY;

    override fun toFields(): List<Field<*>> = when (this) {
        IDENTIFIER -> listOf(COMPETITION_HAVING_RESULTS.IDENTIFIER)
        NAME -> listOf(COMPETITION_HAVING_RESULTS.NAME)
        SHORT_NAME -> listOf(COMPETITION_HAVING_RESULTS.SHORT_NAME)
        CATEGORY -> listOf(COMPETITION_HAVING_RESULTS.CATEGORY)
    }
}