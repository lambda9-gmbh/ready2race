package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentTemplateDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.GAP_DOCUMENT_TEMPLATE_DATA
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.selectOne
import java.util.UUID

object GapDocumentTemplateDataRepo {

    fun create(record: GapDocumentTemplateDataRecord) = GAP_DOCUMENT_TEMPLATE_DATA.insertReturning(record) { TEMPLATE }

    fun getData(template: UUID) = GAP_DOCUMENT_TEMPLATE_DATA.selectOne({ DATA }) { TEMPLATE.eq(template)}
}