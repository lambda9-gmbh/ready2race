package de.lambda9.ready2race.backend.app.participant.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CertificateOfEventParticipationSendingJobRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CERTIFICATE_OF_EVENT_PARTICIPATION_SENDING_JOB
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.Jooq

object CertificateOfEventParticipationSendingJobRepo {

    fun create(records: List<CertificateOfEventParticipationSendingJobRecord>) =
        CERTIFICATE_OF_EVENT_PARTICIPATION_SENDING_JOB.insert(records)

    fun getAndLockNext() = Jooq.query {
        with(CERTIFICATE_OF_EVENT_PARTICIPATION_SENDING_JOB) {
            selectFrom(this)
                .forUpdate()
                .skipLocked()
                .fetchAny()
        }
    }

}