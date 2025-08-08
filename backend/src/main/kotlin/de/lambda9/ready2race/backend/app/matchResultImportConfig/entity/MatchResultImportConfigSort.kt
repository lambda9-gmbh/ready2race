package de.lambda9.ready2race.backend.app.matchResultImportConfig.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.MATCH_RESULT_IMPORT_CONFIG
import de.lambda9.ready2race.backend.database.generated.tables.references.STARTLIST_EXPORT_CONFIG
import org.jooq.Field

enum class MatchResultImportConfigSort : Sortable {
    NAME;

    override fun toFields(): List<Field<*>> = when (this) {
        NAME -> listOf(MATCH_RESULT_IMPORT_CONFIG.NAME)
    }
}