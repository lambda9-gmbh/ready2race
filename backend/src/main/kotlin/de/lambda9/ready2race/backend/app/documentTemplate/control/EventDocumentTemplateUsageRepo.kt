package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentTemplateUsageRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_TEMPLATE_USAGE
import de.lambda9.ready2race.backend.database.insert

object EventDocumentTemplateUsageRepo {

    fun create(record: EventDocumentTemplateUsageRecord) = EVENT_DOCUMENT_TEMPLATE_USAGE.insert(record)

}