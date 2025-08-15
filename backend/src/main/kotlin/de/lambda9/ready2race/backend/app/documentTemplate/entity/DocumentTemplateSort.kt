package de.lambda9.ready2race.backend.app.documentTemplate.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.DOCUMENT_TEMPLATE
import org.jooq.Field

enum class DocumentTemplateSort : Sortable {
    NAME;

    override fun toFields(): List<Field<*>> = when(this) {
        NAME -> listOf(DOCUMENT_TEMPLATE.NAME)
    }
}