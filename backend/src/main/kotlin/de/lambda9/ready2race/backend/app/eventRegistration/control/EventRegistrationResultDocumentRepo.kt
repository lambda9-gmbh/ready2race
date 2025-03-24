package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationResultDocumentRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION_RESULT_DOCUMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION_RESULT_DOCUMENT_DOWNLOAD
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.selectOne
import java.util.UUID

object EventRegistrationResultDocumentRepo {

    fun create(record: EventRegistrationResultDocumentRecord) = EVENT_REGISTRATION_RESULT_DOCUMENT.insertReturning(record) { EVENT }

    fun getDownload(eventId: UUID) = EVENT_REGISTRATION_RESULT_DOCUMENT_DOWNLOAD.selectOne { EVENT.eq(eventId) }

}