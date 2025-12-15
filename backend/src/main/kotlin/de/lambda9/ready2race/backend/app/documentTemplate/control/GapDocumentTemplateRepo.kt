package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateViewSort
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.GapDocumentTemplateView
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.GAP_DOCUMENT_TEMPLATE
import de.lambda9.ready2race.backend.database.generated.tables.references.GAP_DOCUMENT_TEMPLATE_ASSIGNMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.GAP_DOCUMENT_TEMPLATE_VIEW
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import java.util.UUID

object GapDocumentTemplateRepo {

    private fun GapDocumentTemplateView.searchFields() = listOf(NAME)

    fun create(record: GapDocumentTemplateRecord) = GAP_DOCUMENT_TEMPLATE.insertReturning(record) { ID }

    fun exists(id: UUID) = GAP_DOCUMENT_TEMPLATE.exists { ID.eq(id) }

    fun update(id: UUID, f: GapDocumentTemplateRecord.() -> Unit) = GAP_DOCUMENT_TEMPLATE.update(f) { ID.eq(id) }

    fun delete(id: UUID) = GAP_DOCUMENT_TEMPLATE.delete { ID.eq(id) }

    fun get(id: UUID) = GAP_DOCUMENT_TEMPLATE_VIEW.selectOne { ID.eq(id) }

    fun getAssigned(type: GapDocumentType) = GAP_DOCUMENT_TEMPLATE_ASSIGNMENT.selectOne {
        TYPE.eq(type.name)
    }

    fun page(
        params: PaginationParameters<GapDocumentTemplateViewSort>,
    ) = GAP_DOCUMENT_TEMPLATE_VIEW.page(params, { searchFields() })
}