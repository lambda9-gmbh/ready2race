package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationReportRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION_REPORT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION_REPORT_DOWNLOAD
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.selectOne
import java.util.UUID

object EventRegistrationReportRepo {

    fun create(record: EventRegistrationReportRecord) = EVENT_REGISTRATION_REPORT.insertReturning(record) { EVENT }

    fun getDownload(eventId: UUID) = EVENT_REGISTRATION_REPORT_DOWNLOAD.selectOne { EVENT.eq(eventId) }

    fun delete(eventId: UUID) = EVENT_REGISTRATION_REPORT.delete { EVENT.eq(eventId) }

}