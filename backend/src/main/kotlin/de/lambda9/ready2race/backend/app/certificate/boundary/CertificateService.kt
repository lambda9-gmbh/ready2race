package de.lambda9.ready2race.backend.app.certificate.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.auth.entity.Privilege.Scope
import de.lambda9.ready2race.backend.app.certificate.entity.CertificateError
import de.lambda9.ready2race.backend.app.certificate.entity.CertificateJobError
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapPlaceholderLogic
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateUsageRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.toGapPlaceholders
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapPlaceholderValues
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
import de.lambda9.ready2race.backend.docx.DocxPageSize
import de.lambda9.ready2race.backend.docx.gapDocumentsDocx
import de.lambda9.ready2race.backend.docx.toByteArray
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.ready2race.backend.pdf.AdditionalText
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.ready2race.backend.validation.emailPattern
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.transact
import org.apache.pdfbox.Loader
import java.io.ByteArrayOutputStream
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

    /**
     * Wie [participantForEvent], aber wahlweise als DOCX statt PDF — für den manuellen Download.
     * Der E-Mail-Versand ruft weiterhin die zweistellige Überladung auf und bleibt damit bei PDF,
     * ebenso die Vorschau in `GapDocumentTemplateService` — beide bleiben daher außen vor und
     * müssen sich nicht mit `CertificateError` befassen. Gibt deshalb absichtlich ein `App`
     * zurück statt wie die zweistellige Überladung ein rohes `ByteArray`: nur die beiden
     * Download-Endpunkte rufen diese Überladung auf und laufen bereits in einer KIO-Comprehension.
     * Rückgabetyp ist bewusst `IO<...>` (= `KIO<Any, ...>`) statt des Typalias `App` (= `KIO<JEnv,
     * ...>`): die Funktion greift nie auf die Umgebung zu, bleibt also rein bytes-in/bytes-out und
     * lässt sich per `unsafeRunSync()` ohne echtes `JEnv` testen. `internal` statt `private`, damit
     * genau das auch von einem Test in einer anderen Datei aus möglich ist.
     */
    internal fun participantForEvent(
        additions: List<AdditionalText>,
        template: ByteArray,
        fontName: String?,
        format: AwardCertificateService.Format,
    ): IO<CertificateError, ByteArray> = when (format) {
        AwardCertificateService.Format.PDF -> KIO.ok(participantForEvent(additions, template))

        AwardCertificateService.Format.DOCX -> KIO.comprehension {
            // Loader.loadPDF/getPage werfen bei einer defekten oder leeren Vorlage eine Exception,
            // die ohne KIO.effect als untypisierter 500er beim Client ankäme (siehe AwardCertificateService).
            // Anders als bei der Siegerurkunde darf die Teilnahmeurkunden-Vorlage mehrseitig sein,
            // deshalb werden hier alle Seiten eingelesen, nicht nur die erste.
            val templatePageSizes = !KIO.effect {
                val templateDoc = Loader.loadPDF(template)
                try {
                    // getPage(0) wirft bei einer leeren Vorlage (numberOfPages == 0) absichtlich,
                    // statt eine leere Seitengrößen-Liste durchzureichen: gapDocumentsDocx würde
                    // daraus sonst ein gültiges, aber inhaltsleeres .docx ohne Seitengröße erzeugen.
                    templateDoc.getPage(0)
                    (0 until templateDoc.numberOfPages).map { pageIndex ->
                        val mediaBox = templateDoc.getPage(pageIndex).mediaBox
                        DocxPageSize(mediaBox.width, mediaBox.height)
                    }
                } finally {
                    templateDoc.close()
                }
            }.mapError { CertificateError.UnreadableTemplate }

            KIO.ok(
                gapDocumentsDocx(
                    templatePageSizes = templatePageSizes,
                    fontName = fontName,
                    certificates = listOf(additions),
                ).toByteArray()
            )
        }
    }

    fun downloadCertificatesOfParticipation(
        eventId: UUID,
        clubId: UUID,
        format: AwardCertificateService.Format,
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

        // Erst alle Urkunden per KIO erzeugen (dabei kann Format.DOCX typisiert fehlschlagen),
        // danach erst die Bytes einsammeln und ins ZIP schreiben — `forEach` selbst ist kein
        // KIO-Kontext und könnte den Fehler nicht typisiert nach oben reichen.
        val certificates = !participantResults.values
            .filter { it.isNotEmpty() }
            .traverse { participantResultList ->
                KIO.comprehension {
                    val result = participantResultList.first()

                    val resultTotal = participantResultList.sumOf { it.teamResultValue ?: 0 }

                    val bytes = !participantForEvent(
                        additions = GapPlaceholderLogic.fill(
                            placeholders = template.placeholders!!.toList().toGapPlaceholders(),
                            values = GapPlaceholderValues(
                                firstName = result.firstname ?: "",
                                lastName = result.lastname ?: "",
                                fullName = "${result.firstname ?: ""} ${result.lastname ?: ""}",
                                result = "$resultTotal $resultUnit",
                                eventName = event.name,
                            ),
                        ),
                        template = template.data!!,
                        fontName = template.fontName,
                        format = format,
                    )

                    val fileName =
                        "certificate_of_participation_${event.name}_${result.firstname}_${result.lastname}.${format.extension}"

                    KIO.ok(fileName to bytes)
                }
            }

        val zipOutputStream = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zipOutputStream).use { zip ->
            certificates.forEach { (fileName, bytes) ->
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
        format: AwardCertificateService.Format,
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

        val bytes = !participantForEvent(
            additions = GapPlaceholderLogic.fill(
                placeholders = template.placeholders!!.toList().toGapPlaceholders(),
                values = GapPlaceholderValues(
                    firstName = participant.firstname,
                    lastName = participant.lastname,
                    fullName = "${participant.firstname} ${participant.lastname}",
                    result = "$resultTotal $resultUnit",
                    eventName = event.name,
                ),
            ),
            template = template.data!!,
            fontName = template.fontName,
            format = format,
        )

        KIO.ok(
            ApiResponse.File(
                name = "certificate_of_participation_${event.name}_${participant.firstname}_${participant.lastname}.${format.extension}",
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
            additions = GapPlaceholderLogic.fill(
                placeholders = template.placeholders!!.toList().toGapPlaceholders(),
                values = GapPlaceholderValues(
                    firstName = participant.firstname,
                    lastName = participant.lastname,
                    fullName = "${participant.firstname} ${participant.lastname}",
                    result = "$resultTotal $resultUnit",
                    eventName = event.name,
                ),
            ),
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