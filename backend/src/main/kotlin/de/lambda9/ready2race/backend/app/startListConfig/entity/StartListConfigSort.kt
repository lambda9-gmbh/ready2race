package de.lambda9.ready2race.backend.app.startListConfig.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.STARTLIST_EXPORT_CONFIG
import org.jooq.Field

enum class StartListConfigSort : Sortable {
    NAME;

    override fun toFields(): List<Field<*>> = when (this) {
        NAME -> listOf(STARTLIST_EXPORT_CONFIG.NAME)
    }
}