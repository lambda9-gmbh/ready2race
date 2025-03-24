package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationResultDocumentDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION_RESULT_DOCUMENT_DATA
import de.lambda9.ready2race.backend.database.insert

object EventRegistrationResultDocumentDataRepo {

    fun create(record: EventRegistrationResultDocumentDataRecord) = EVENT_REGISTRATION_RESULT_DOCUMENT_DATA.insert(record)

}