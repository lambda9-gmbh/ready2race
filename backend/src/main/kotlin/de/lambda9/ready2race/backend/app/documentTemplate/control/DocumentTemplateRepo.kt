package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentTemplateSort
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentType
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.DocumentTemplate
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.DOCUMENT_TEMPLATE
import de.lambda9.ready2race.backend.database.generated.tables.references.DOCUMENT_TEMPLATE_ASSIGNMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.DOCUMENT_TEMPLATE_DATA
import de.lambda9.ready2race.backend.database.generated.tables.references.DOCUMENT_TEMPLATE_USAGE
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_TEMPLATE_USAGE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object DocumentTemplateRepo {

    private fun DocumentTemplate.searchFields() = listOf(NAME)

    fun exists(id: UUID) = DOCUMENT_TEMPLATE.exists { ID.eq(id) }

    fun get(id: UUID) = DOCUMENT_TEMPLATE.selectOne { ID.eq(id) }

    fun create(record: DocumentTemplateRecord) = DOCUMENT_TEMPLATE.insertReturning(record) { ID }

    fun update(id: UUID, f: DocumentTemplateRecord.() -> Unit) = DOCUMENT_TEMPLATE.update(f) { ID.eq(id) }

    fun delete(id: UUID) = DOCUMENT_TEMPLATE.delete { ID.eq(id) }

    fun getAssigned(documentType: DocumentType, eventId: UUID) = DOCUMENT_TEMPLATE_ASSIGNMENT.selectOne {
        DSL.and(
            DOCUMENT_TYPE.eq(documentType.name),
            DSL.or(
                EVENT.eq(eventId),
                DSL.and(
                    EVENT.isNull,
                    DSL.notExists(
                        DSL.select().from(this).where(DOCUMENT_TYPE.eq(documentType.name)).and(EVENT.eq(eventId))
                    )
                )
            )
        )
    }

    fun count(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(DOCUMENT_TEMPLATE) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<DocumentTemplateSort>,
    ): JIO<List<DocumentTemplateRecord>> = Jooq.query {
        with(DOCUMENT_TEMPLATE) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

}