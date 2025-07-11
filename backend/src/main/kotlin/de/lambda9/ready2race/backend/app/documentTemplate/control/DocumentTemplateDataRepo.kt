package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.DOCUMENT_TEMPLATE_DATA
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.selectOne
import java.util.UUID

object DocumentTemplateDataRepo {

    fun create(record: DocumentTemplateDataRecord) = DOCUMENT_TEMPLATE_DATA.insertReturning(record) { TEMPLATE }

    fun getData(id: UUID) = DOCUMENT_TEMPLATE_DATA.selectOne({ DATA }) { TEMPLATE.eq(id) }
}