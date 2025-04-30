package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationReportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION_REPORT_DATA
import de.lambda9.ready2race.backend.database.insert

object EventRegistrationReportDataRepo {

    fun create(record: EventRegistrationReportDataRecord) = EVENT_REGISTRATION_REPORT_DATA.insert(record)

}