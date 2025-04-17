package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateUsageRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.DOCUMENT_TEMPLATE_USAGE
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.core.extensions.kio.onNull
import de.lambda9.tailwind.jooq.JIO

object DocumentTemplateUsageRepo {

    fun upsert(
        record: DocumentTemplateUsageRecord,
    ): JIO<DocumentTemplateUsageRecord> =
        DOCUMENT_TEMPLATE_USAGE.update(f = { template = record.template }) { DOCUMENT_TYPE.eq(record.documentType) }
            .onNull {
                DOCUMENT_TEMPLATE_USAGE.insertReturning(record)
            }

}