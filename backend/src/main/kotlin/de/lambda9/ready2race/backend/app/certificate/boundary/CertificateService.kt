package de.lambda9.ready2race.backend.app.certificate.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.auth.entity.Privilege.Scope
import de.lambda9.ready2race.backend.app.certificate.entity.CertificateError
import de.lambda9.ready2race.backend.app.certificate.entity.CertificateJobError
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateUsageRepo
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.entity.EmailAttachment
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplatePlaceholder
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.event.entity.MatchResultType
import de.lambda9.ready2race.backend.app.participant.control.CertificateOfEventParticipationSendingJobRepo
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.results.control.ChallengeResultParticipantViewRepo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.AppUserWithPrivileges
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CertificateOfEventParticipationSendingJobRecord
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.ready2race.backend.pdf.AdditionalText
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.ready2race.backend.text.TextAlign
import de.lambda9.ready2race.backend.validation.emailPattern
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.jooq.transact
import java.io.ByteArrayOutputStream
import java.lang.Exception
import java.time.LocalDateTime
import java.util.UUID

object CertificateService {

    fun participantForEvent(
        additions: List<AdditionalText>,
        template: ByteArray,
    ): ByteArray {
        val doc = document(template, additions)

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()

        val bytes = out.toByteArray()
        out.close()

        return bytes
    }

    fun downloadCertificatesOfParticipation(
        eventId: UUID,
        clubId: UUID,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        val type = GapDocumentType.CERTIFICATE_OF_PARTICIPATION

        val template = !GapDocumentTemplateRepo.getAssigned(type).orDie()
            .onNullFail { CertificateError.MissingTemplate }

        val event = !EventRepo.get(eventId).orDie().onNullFail{ EventError.NotFound }
            .failIf({ it.challengeEvent != true }) { CertificateError.NotAChallengeEvent }

        !CompetitionRepo.getByEvent(eventId).orDie()
            .failIf({it.any { c -> c.challengeEndAt!! > LocalDateTime.now() }}) { CertificateError.ChallengeStillInProgress }

        val results = !ChallengeResultParticipantViewRepo.getByEventIdAndClubId(
            eventId = eventId,
            clubId = clubId,
            verifiedIfNeededOnly = true,
        ).orDie()

        val participantResults = results.groupBy { it.id }

        val resultUnit = MatchResultType.valueOf(event.challengeMatchResultType!!).unit

        val zipOutputStream = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zipOutputStream).use { zip ->
            participantResults.forEach { (_, participantResultList) ->
                if (participantResultList.isEmpty()) return@forEach

                val result = participantResultList.first()

                val resultTotal = participantResultList.sumOf { it.teamResultValue ?: 0 }

                val bytes = participantForEvent(
                    additions = template.placeholders!!.mapNotNull {
                        val placeholderType =
                            try {
                                GapDocumentPlaceholderType.valueOf(it!!.type)
                            } catch (ex: Exception) {
                                return@mapNotNull null
                            }

                        AdditionalText(
                            content = when (placeholderType) {
                                GapDocumentPlaceholderType.FIRST_NAME -> result.firstname ?: ""
                                GapDocumentPlaceholderType.LAST_NAME -> result.lastname ?: ""
                                GapDocumentPlaceholderType.FULL_NAME -> "${result.firstname ?: ""} ${result.lastname ?: ""}"
                                GapDocumentPlaceholderType.RESULT -> "$resultTotal $resultUnit"
                                GapDocumentPlaceholderType.EVENT_NAME -> event.name
                            },
                            page = it.page,
                            relLeft = it.relLeft,
                            relTop = it.relTop,
                            relWidth = it.relWidth,
                            relHeight = it.relHeight,
                            textAlign = TextAlign.valueOf(it.textAlign),
                        )
                    },
                    template = template.data!!,
                )

                // Add PDF to ZIP
                val fileName = "certificate_of_participation_${event.name}_${result.firstname}_${result.lastname}.pdf"
                val zipEntry = java.util.zip.ZipEntry(fileName)
                zip.putNextEntry(zipEntry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }

        KIO.ok(
            ApiResponse.File(
                name = "certificates_of_participation_${event.name}.zip",
                bytes = zipOutputStream.toByteArray(),
            )
        )
    }

    fun downloadCertificateOfParticipation(
        eventId: UUID,
        participantId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Scope,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        val type = GapDocumentType.CERTIFICATE_OF_PARTICIPATION

        val template = !GapDocumentTemplateRepo.getAssigned(type).orDie()
            .onNullFail { CertificateError.MissingTemplate }

        val participant = !ParticipantRepo.get(participantId).orDie().onNullFail { ParticipantError.ParticipantNotFound }

        !KIO.failOn(scope != Scope.GLOBAL && user.club != participant.club) { AuthError.PrivilegeMissing }

        val event = !EventRepo.get(eventId).orDie().onNullFail{ EventError.NotFound }
            .failIf({ it.challengeEvent != true }) { CertificateError.NotAChallengeEvent }

        !CompetitionRepo.getByEvent(eventId).orDie()
            .failIf({it.any { c -> c.challengeEndAt!! > LocalDateTime.now() }}) { CertificateError.ChallengeStillInProgress }

        val result = !ChallengeResultParticipantViewRepo.getByEventIdAndParticipantId(
            eventId = eventId,
            participantId = participantId,
            verifiedIfNeededOnly = true,
        ).orDie()
            .onNullFail { CertificateError.NoResults }

        val resultTotal = result.sumOf { it.teamResultValue ?: 0 }
        val resultUnit = MatchResultType.valueOf(event.challengeMatchResultType!!).unit

        val bytes = participantForEvent(
            additions = template.placeholders!!.mapNotNull {
                val type =
                    try {
                        GapDocumentPlaceholderType.valueOf(it!!.type)
                    } catch (ex: Exception) {
                        return@mapNotNull null
                    }

                AdditionalText(
                    content = when (type) {
                        GapDocumentPlaceholderType.FIRST_NAME -> participant.firstname
                        GapDocumentPlaceholderType.LAST_NAME -> participant.lastname
                        GapDocumentPlaceholderType.FULL_NAME -> "${participant.firstname} ${participant.lastname}"
                        GapDocumentPlaceholderType.RESULT -> "$resultTotal $resultUnit"
                        GapDocumentPlaceholderType.EVENT_NAME -> event.name
                    },
                    page = it.page,
                    relLeft = it.relLeft,
                    relTop = it.relTop,
                    relWidth = it.relWidth,
                    relHeight = it.relHeight,
                    textAlign = TextAlign.valueOf(it.textAlign),
                )
            },
            template = template.data!!,
        )

        KIO.ok(
            ApiResponse.File(
                name = "certificate_of_participation_${event.name}_${participant.firstname}_${participant.lastname}.pdf",
                bytes = bytes,
            )
        )
    }

    fun createCertificateOfParticipationJobs(
        eventId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !EventService.checkIsChallengeEvent(eventId).onFalseFail { CertificateError.NotAChallengeEvent }

        !CompetitionRepo.getByEvent(eventId).orDie()
            .failIf({it.any { c -> c.challengeEndAt!! > LocalDateTime.now() }}) { CertificateError.ChallengeStillInProgress }

        val participantIds = !ChallengeResultParticipantViewRepo.getForCertificates(eventId).orDie()

        !CertificateOfEventParticipationSendingJobRepo.create(
            participantIds.map {
                CertificateOfEventParticipationSendingJobRecord(
                    id = UUID.randomUUID(),
                    event = eventId,
                    participant = it
                )
            }
        ).orDie()

        noData
    }

    fun sendNextCertificateOfParticipation(): App<CertificateJobError, Unit> = KIO.comprehension {
        val type = GapDocumentType.CERTIFICATE_OF_PARTICIPATION

        val template = !GapDocumentTemplateRepo.getAssigned(type).orDie()
            .onNullFail { CertificateJobError.MissingTemplate(type) }

        val job = !CertificateOfEventParticipationSendingJobRepo.getAndLockNext().orDie()
            .onNullFail { CertificateJobError.NoOpenJobs }

        val participant = !ParticipantRepo.get(job.participant).orDie().onNullDie("fetching referenced row")
            .failIf({ it.email.isNullOrBlank() || !emailPattern.matches(it.email!!) }) { CertificateJobError.MissingParticipantEmail(job.participant) }
        val event = !EventRepo.get(job.event).orDie().onNullDie("fetching referenced row, not null column")

        val result = !ChallengeResultParticipantViewRepo.getByEventIdAndParticipantId(
            eventId = job.event,
            participantId = job.participant,
            verifiedIfNeededOnly = true,
        ).orDie()
            .onNullFail { CertificateJobError.NoResults(job.participant) }

        val resultTotal = result.sumOf { it.teamResultValue ?: 0 }
        val resultUnit = MatchResultType.valueOf(event.challengeMatchResultType!!).unit

        val bytes = participantForEvent(
            additions = template.placeholders!!.mapNotNull {
                val type =
                    try {
                        GapDocumentPlaceholderType.valueOf(it!!.type)
                    } catch (ex: Exception) {
                        return@mapNotNull null
                    }

                AdditionalText(
                    content = when (type) {
                        GapDocumentPlaceholderType.FIRST_NAME -> participant.firstname
                        GapDocumentPlaceholderType.LAST_NAME -> participant.lastname
                        GapDocumentPlaceholderType.FULL_NAME -> "${participant.firstname} ${participant.lastname}"
                        GapDocumentPlaceholderType.RESULT -> "$resultTotal $resultUnit"
                        GapDocumentPlaceholderType.EVENT_NAME -> event.name
                    },
                    page = it.page,
                    relLeft = it.relLeft,
                    relTop = it.relTop,
                    relWidth = it.relWidth,
                    relHeight = it.relHeight,
                    textAlign = TextAlign.valueOf(it.textAlign),
                )
            },
            template = template.data!!,
        )

        val content = !EmailService.getTemplate(
            EmailTemplateKey.CERTIFICATE_OF_PARTICIPATION_PARTICIPANT,
            EmailLanguage.DE,
        ).map { template ->
            template.toContent(
                EmailTemplatePlaceholder.RECIPIENT to "${participant.firstname} ${participant.lastname}",
                EmailTemplatePlaceholder.EVENT to event.name,
            )
        }

        !EmailService.enqueue(
            recipient = participant.email!!,
            content = content,
            attachments = listOf(
                EmailAttachment(
                    name = "certificate_of_participation_${event.name}_${participant.firstname}_${participant.lastname}.pdf",
                    data = bytes,
                )
            )
        )

        job.delete()

        unit
    }.transact()
}