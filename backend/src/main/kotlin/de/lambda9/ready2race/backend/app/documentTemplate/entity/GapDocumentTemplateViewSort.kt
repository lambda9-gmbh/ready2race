package de.lambda9.ready2race.backend.app.documentTemplate.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.GAP_DOCUMENT_TEMPLATE_VIEW
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class GapDocumentTemplateViewSort : Sortable {
    NAME;

    override fun toFields(): List<Field<*>> = when(this) {
        NAME -> listOf(GAP_DOCUMENT_TEMPLATE_VIEW.NAME)
    }
}