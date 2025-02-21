package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class ClubSort : Sortable {
    ID,
    NAME,
    CREATED_AT;

    override fun toField(): Field<*> = when (this) {
        ID -> CLUB.ID
        NAME -> CLUB.NAME
        CREATED_AT -> CLUB.CREATED_AT
    }
}