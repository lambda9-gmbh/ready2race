package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentType
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateUsageRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.DOCUMENT_TEMPLATE_USAGE
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.core.extensions.kio.onNull
import de.lambda9.tailwind.jooq.JIO
import org.jooq.impl.DSL

object DocumentTemplateUsageRepo {

    fun upsert(
        record: DocumentTemplateUsageRecord,
    ): JIO<DocumentTemplateUsageRecord> =
        DOCUMENT_TEMPLATE_USAGE.update(f = { template = record.template }) { DOCUMENT_TYPE.eq(record.documentType) }
            .onNull {
                DOCUMENT_TEMPLATE_USAGE.insertReturning(record)
            }

    fun all() = DOCUMENT_TEMPLATE_USAGE.select { DSL.trueCondition() }

    fun delete(type: DocumentType) = DOCUMENT_TEMPLATE_USAGE.delete { DOCUMENT_TYPE.eq(type.name) }

}