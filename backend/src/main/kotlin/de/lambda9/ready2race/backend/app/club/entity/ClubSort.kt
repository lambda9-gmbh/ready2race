package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import org.jooq.Field

enum class ClubSort : Sortable {
    ID,
    NAME,
    CREATED_AT;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(CLUB.ID)
        NAME -> listOf(CLUB.NAME)
        CREATED_AT -> listOf(CLUB.CREATED_AT)
    }
}