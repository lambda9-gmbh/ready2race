package de.lambda9.ready2race.backend.app.certificate.entity

import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import java.util.UUID

sealed interface CertificateJobError {

    data object NoOpenJobs : CertificateJobError

    data class NoResults(val participantId: UUID) : CertificateJobError
    data class MissingParticipantEmail(val participant: UUID) : CertificateJobError
    data class MissingTemplate(val type: GapDocumentType) : CertificateJobError
}