package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentType
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.DOCUMENT_TEMPLATE
import de.lambda9.ready2race.backend.database.generated.tables.references.DOCUMENT_TEMPLATE_ASSIGNMENT
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.selectOne
import org.jooq.impl.DSL
import java.util.UUID

object DocumentTemplateRepo {

    fun create(record: DocumentTemplateRecord) = DOCUMENT_TEMPLATE.insertReturning(record) { ID }

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

}