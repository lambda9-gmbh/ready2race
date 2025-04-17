package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentTemplateUsageRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_TEMPLATE_USAGE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.core.extensions.kio.onNull
import de.lambda9.tailwind.jooq.JIO
import org.jooq.impl.DSL
import java.util.*

object EventDocumentTemplateUsageRepo {

    fun upsert(
        record: EventDocumentTemplateUsageRecord,
    ): JIO<EventDocumentTemplateUsageRecord> =
        EVENT_DOCUMENT_TEMPLATE_USAGE.update(f = { template = record.template }) {
            DSL.and(
                DOCUMENT_TYPE.eq(record.documentType),
                EVENT.eq(record.event)
            )
        }
            .onNull {
                EVENT_DOCUMENT_TEMPLATE_USAGE.insertReturning(record)
            }

    fun getByEvent(eventId: UUID) = EVENT_DOCUMENT_TEMPLATE_USAGE.select { EVENT.eq(eventId) }
}