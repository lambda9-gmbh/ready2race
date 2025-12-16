package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentPlaceholderRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.GAP_DOCUMENT_PLACEHOLDER
import de.lambda9.ready2race.backend.database.insert
import java.util.UUID

object GapDocumentPlaceholderRepo {

    fun create(records: List<GapDocumentPlaceholderRecord>) = GAP_DOCUMENT_PLACEHOLDER.insert(records)

    fun deleteByTemplate(template: UUID) = GAP_DOCUMENT_PLACEHOLDER.delete { TEMPLATE.eq(template) }
}