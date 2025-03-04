package de.lambda9.ready2race.backend.app.eventDocument.control

import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_DATA
import de.lambda9.ready2race.backend.database.insertReturning

object EventDocumentDataRepo {

    fun create(record: EventDocumentDataRecord) = EVENT_DOCUMENT_DATA.insertReturning(record) { EVENT_DOCUMENT }
}