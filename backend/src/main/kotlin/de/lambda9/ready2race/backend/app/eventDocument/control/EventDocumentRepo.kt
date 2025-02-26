package de.lambda9.ready2race.backend.app.eventDocument.control

import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT
import de.lambda9.ready2race.backend.database.insertReturning

object EventDocumentRepo {

    fun create(record: EventDocumentRecord) = EVENT_DOCUMENT.insertReturning(record) { ID }
}