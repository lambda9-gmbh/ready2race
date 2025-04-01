package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.DOCUMENT_TEMPLATE_DATA
import de.lambda9.ready2race.backend.database.insertReturning

object DocumentTemplateDataRepo {

    fun create(record: DocumentTemplateDataRecord) = DOCUMENT_TEMPLATE_DATA.insertReturning(record) { TEMPLATE }

}