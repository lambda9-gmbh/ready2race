package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateUsageRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.DOCUMENT_TEMPLATE_USAGE
import de.lambda9.ready2race.backend.database.insert

object DocumentTemplateUsageRepo {

    fun create(record: DocumentTemplateUsageRecord) = DOCUMENT_TEMPLATE_USAGE.insert(record)

}