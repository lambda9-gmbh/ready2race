package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentTemplateUsageRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.GAP_DOCUMENT_TEMPLATE_USAGE
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.core.extensions.kio.onNull
import org.jooq.impl.DSL

object GapDocumentTemplateUsageRepo {

    fun upsert(
        record: GapDocumentTemplateUsageRecord,
    ) = GAP_DOCUMENT_TEMPLATE_USAGE.update(f = { template = record.template }) { TYPE.eq(record.type)}
        .onNull { GAP_DOCUMENT_TEMPLATE_USAGE.insertReturning(record) }

    fun all() = GAP_DOCUMENT_TEMPLATE_USAGE.select { DSL.trueCondition() }

    fun delete(type: GapDocumentType) = GAP_DOCUMENT_TEMPLATE_USAGE.delete { TYPE.eq(type.name) }
}