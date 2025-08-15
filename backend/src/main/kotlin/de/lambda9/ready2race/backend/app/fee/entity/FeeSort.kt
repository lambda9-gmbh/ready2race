package de.lambda9.ready2race.backend.app.fee.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.FEE
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class FeeSort : Sortable {
    ID,
    NAME;

    override fun toFields(): List<Field<*>> = when(this) {
        ID -> listOf(FEE.ID)
        NAME -> listOf(FEE.NAME)
    }
}