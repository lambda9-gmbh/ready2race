package de.lambda9.ready2race.backend.app.certificate.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateEntry
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateError
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateOptions
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateParticipant
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateTeam
import de.lambda9.ready2race.backend.app.club.boundary.ClubComposition
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapPlaceholderLogic
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.toGapPlaceholders
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.docx.DocxPageSize
import de.lambda9.ready2race.backend.docx.gapDocumentsDocx
import de.lambda9.ready2race.backend.docx.toByteArray
import de.lambda9.ready2race.backend.lexiNumberComp
import de.lambda9.ready2race.backend.pdf.gapDocuments
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import org.apache.pdfbox.Loader
import java.io.ByteArrayOutputStream
import java.util.UUID

object AwardCertificateService {

    enum class Format {
        PDF, DOCX;

        val extension: String
            get() = when (this) {
                PDF -> "pdf"
                DOCX -> "docx"
            }
    }

    fun downloadForEvent(
        eventId: UUID,
        options: AwardCertificateOptions,
        format: Format,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }
        val entries = !entriesForEvent(eventId, options, competitionId = null, registrationId = null)

        render(event, entries, options, format, "urkunden_${event.name}")
    }

    fun downloadForCompetition(
        eventId: UUID,
        competitionId: UUID,
        options: AwardCertificateOptions,
        format: Format,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }
        val entries = !entriesForEvent(eventId, options, competitionId, registrationId = null)
        val identifier = entries.firstOrNull()?.competitionIdentifier ?: ""

        render(event, entries, options, format, "urkunden_${event.name}_$identifier")
    }

    fun downloadForRegistration(
        eventId: UUID,
        competitionId: UUID,
        registrationId: UUID,
        options: AwardCertificateOptions,
        format: Format,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }
        val entries = !entriesForEvent(eventId, options, competitionId, registrationId)
        val first = entries.firstOrNull()
        val name = listOfNotNull(
            "urkunde",
            event.name,
            first?.competitionIdentifier,
            first?.place?.toString(),
            first?.names?.firstOrNull(),
        ).joinToString("_")

        render(event, entries, options, format, name)
    }

    /**
     * Sammelt die Urkunden der Veranstaltung, optional auf einen Wettkampf und eine Meldung
     * eingegrenzt. Die Wettkämpfe werden wie in der Ergebnisliste nach Identifier sortiert.
     * Das Event selbst wird hier nicht (mehr) geladen — das übernimmt die aufrufende Funktion,
     * die den Datensatz auch für den Dateinamen und `render` benötigt.
     */
    private fun entriesForEvent(
        eventId: UUID,
        options: AwardCertificateOptions,
        competitionId: UUID?,
        registrationId: UUID?,
    ): App<ServiceError, List<AwardCertificateEntry>> = KIO.comprehension {
        // Ein Challenge-Event kennt weder Läufe noch Platzierungen - Siegerurkunden gibt es dort
        // grundsätzlich nicht. Ohne diese Prüfung lief der Fall in NoResults ("keine platzierten
        // Teams"): richtiges Ergebnis, falsche Begründung, denn das klingt nach "noch nicht
        // fertig" und lässt das Büro auf Ergebnisse warten, die nie kommen. Steht bewusst vor
        // allem anderen, damit die Antwort nicht davon abhängt, was das Event sonst enthält.
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { AwardCertificateError.IsChallengeEvent }

        // Der Einzeldownload dient Nachdrucken und Korrekturen einer bestimmten Urkunde, daher
        // darf die Platzgrenze dort nicht greifen. `null` bedeutet in AwardCertificateLogic
        // "unbegrenzt", statt einen Sentinel-Wert durch die Options zu schmuggeln.
        val maxPlace = if (registrationId == null) options.maxPlace else null

        val competitions = !CompetitionRepo.getByEvent(eventId).orDie()

        val selected = if (competitionId == null) {
            competitions
        } else {
            val match = competitions.filter { it.id == competitionId }
            !KIO.failOn(match.isEmpty()) { AwardCertificateError.CompetitionNotInEvent }
            match
        }

        val entries = !selected
            .sortedWith(lexiNumberComp { it.identifier })
            .traverse { competition ->
                KIO.comprehension {
                    val places = !CompetitionExecutionService.computeCompetitionPlaces(competition.id!!)

                    val teams = places.map { (team, place) ->
                        // Die Urkunde zeigt die Vereine der Athleten in voller Länge und ohne jede
                        // Kürzung - auch ohne heuristische (deshalb `emptyMap()`): sie geht in die
                        // Hand des Ruderers und hängt danach im Bootshaus, da hat "RC Nürtingen"
                        // nichts verloren. Bis zum 09.08.2026 stand hier bei gemischter Crew das
                        // pauschale "Renngemeinschaft".
                        val clubs = ClubComposition.of(
                            team.participants.map {
                                ClubComposition.clubWorn(it.external, it.externalClubName, it.clubName)
                            },
                            emptyMap(),
                        )
                        val clubName = clubs.full.ifEmpty { null } ?: team.clubName

                        AwardCertificateTeam(
                            place = place,
                            clubName = clubName,
                            teamName = team.registrationName,
                            result = team.timeString,
                            startNumber = team.startNumber,
                            excluded = team.deregistered || team.out || team.failed,
                            participants = team.participants.map {
                                AwardCertificateParticipant(
                                    firstName = it.firstName,
                                    lastName = it.lastName,
                                    role = it.namedParticipantName,
                                )
                            },
                            registrationId = team.competitionRegistration,
                        )
                    }.filter { registrationId == null || it.registrationId == registrationId }

                    KIO.ok(
                        AwardCertificateLogic.entriesForCompetition(
                            competitionIdentifier = competition.identifier!!,
                            competitionName = competition.name!!,
                            competitionShortName = competition.shortName,
                            teams = teams,
                            mode = options.mode,
                            maxPlace = maxPlace,
                        )
                    )
                }
            }
            .map { it.flatten() }

        !KIO.failOn(entries.isEmpty()) { AwardCertificateError.NoResults }

        KIO.ok(entries)
    }

    private fun render(
        event: EventRecord,
        entries: List<AwardCertificateEntry>,
        options: AwardCertificateOptions,
        format: Format,
        fileBaseName: String,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        val template = !GapDocumentTemplateRepo.getAssigned(GapDocumentType.AWARD_CERTIFICATE).orDie()
            .onNullFail { AwardCertificateError.MissingTemplate }

        val eventDays = !EventDayRepo.getByEvent(event.id!!).orDie()
        val eventDate = AwardCertificateLogic.formatEventDate(eventDays.map { it.date })

        val placeholders = template.placeholders!!.toList().toGapPlaceholders()

        val pages = entries.map { entry ->
            GapPlaceholderLogic.fill(
                placeholders = placeholders,
                values = AwardCertificateLogic.placeholderValues(
                    entry = entry,
                    eventName = event.name,
                    eventLocation = event.location,
                    eventDate = eventDate,
                ),
            )
        }

        val bytes = when (format) {
            Format.PDF -> {
                // Ohne KIO.effect käme jede Exception aus gapDocuments (z. B. eine defekte Vorlage
                // oder ein Zeichen, das trotz Sanitisierung nicht kodierbar ist) als untypisierter
                // 500er beim Client an, statt als der bekannte UnreadableTemplate-Fehler.
                !KIO.effect {
                    val doc = gapDocuments(
                        template = template.data!!,
                        font = template.fontData,
                        withBackground = options.withBackground,
                        pages = pages,
                    )
                    val out = ByteArrayOutputStream()
                    doc.save(out)
                    doc.close()
                    out.toByteArray()
                }.mapError { AwardCertificateError.UnreadableTemplate }
            }

            Format.DOCX -> {
                // Loader.loadPDF/getPage werfen bei einer defekten oder leeren Vorlage eine
                // Exception, die ohne KIO.effect als untypisierter 500er beim Client ankäme.
                val (width, height) = !KIO.effect {
                    val templateDoc = Loader.loadPDF(template.data!!)
                    try {
                        val mediaBox = templateDoc.getPage(0).mediaBox
                        mediaBox.width to mediaBox.height
                    } finally {
                        templateDoc.close()
                    }
                }.mapError { AwardCertificateError.UnreadableTemplate }

                gapDocumentsDocx(
                    templatePageSizes = listOf(DocxPageSize(width, height)),
                    fontName = template.fontName,
                    certificates = pages,
                ).toByteArray()
            }
        }

        KIO.ok(
            ApiResponse.File(
                name = "$fileBaseName.${format.extension}",
                bytes = bytes,
            )
        )
    }
}
