package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidate
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidateParticipant
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyChoiceDto
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyError
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyKeyRequest
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySelectionRequest
import de.lambda9.ready2race.backend.app.awardCeremony.entity.ResultListOptions
import de.lambda9.ready2race.backend.app.certificate.boundary.AwardCertificateLogic
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.eventInfo.entity.BoardCeremonyDto
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.ratingcategory.boundary.RankedEntry
import de.lambda9.ready2race.backend.app.ratingcategory.boundary.RatingCategoryRanking
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionViewRecord
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.lexiNumberComp
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Der Siegerehrungsbogen einer Veranstaltung: welche Ehrungen es gibt, und die ausgewählten davon
 * als druckfertiges PDF.
 *
 * Die Ehrungen werden nicht gespeichert, sondern bei jedem Aufruf aus der Platzberechnung
 * abgeleitet. Das ist Absicht: bis zur Ehrung ändern sich Ergebnisse noch (Nachwertung,
 * Disqualifikation, Ummeldung), und ein gespeicherter Stand wäre genau der, der auf dem Pult
 * falsch ist.
 */
object AwardCeremonyService {

    /** Eine Ehrung mitsamt ihren Booten - die Auswahlzeile allein trägt zu wenig für das Blatt. */
    private data class Ceremony(
        val choice: AwardCeremonyChoiceDto,
        /** Die fertige Rangliste der Wertung, wie RatingCategoryRanking sie gezählt hat. */
        val entries: List<RankedEntry<AwardCeremonyCandidate>>,
    )

    /**
     * Die wählbaren Ehrungen, mit [competitionId] auf einen Wettkampf eingeschränkt.
     *
     * Die Einschränkung ist keine Bequemlichkeit für die Anzeige: jede Ehrung kostet eine
     * Platzberechnung, und die Auswahl wird von der Platzierungsseite eines einzelnen Wettkampfs
     * aus geöffnet. Ohne sie rechnete ein Klick dort die ganze Regatta durch.
     */
    fun listCeremonies(
        eventId: UUID,
        competitionId: UUID? = null,
    ): App<ServiceError, ApiResponse.ListDto<AwardCeremonyChoiceDto>> =
        KIO.comprehension {
            // Steht bewusst vor allem anderen: siehe AwardCeremonyError.IsChallengeEvent.
            !EventService.checkIsChallengeEvent(eventId).onTrueFail { AwardCeremonyError.IsChallengeEvent }

            val ceremonies = !collect(eventId, competitionIds = competitionId?.let { listOf(it) })
            KIO.ok(ApiResponse.ListDto(ceremonies.map { it.choice }))
        }

    fun download(
        eventId: UUID,
        request: AwardCeremonySelectionRequest,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        // Steht bewusst vor allem anderen: siehe AwardCeremonyError.IsChallengeEvent.
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { AwardCeremonyError.IsChallengeEvent }

        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }
        val eventDays = !EventDayRepo.getByEvent(eventId).orDie()
        val eventDate = AwardCertificateLogic.formatEventDate(eventDays.map { it.date })

        // Keine Auswahl heißt „alles drucken" - eine leere Liste ist derselbe Fall wie gar keine.
        val selection = request.selection?.takeIf { it.isNotEmpty() }
        val all = !collect(eventId, competitionIds = selection?.map { it.competitionId }?.distinct())

        val chosen = if (selection == null) all else {
            // Jede angeforderte Ehrung muss es geben - eine still verschluckte Auswahl wäre auf
            // dem Pult ein fehlendes Blatt, das niemand bemerkt, bis die Sprecherin danach greift.
            !KIO.failOn(selection.any { key -> all.none { it.matches(key) } }) {
                AwardCeremonyError.UnknownRatingCategory
            }
            // Reihenfolge kommt aus `all`, nicht aus der Auswahl: gedruckt wird nach Rennnummer,
            // egal in welcher Reihenfolge das Büro angeklickt hat.
            all.filter { ceremony -> selection.any { ceremony.matches(it) } }
        }

        // AwardCeremonyPdf.render wirft bei leerer Liste - ein PDF ohne Seiten öffnet kein
        // Betrachter. Der Fall gehört hierher, wo er noch eine Begründung hat.
        !KIO.failOn(chosen.isEmpty()) { AwardCeremonyError.NoResults }

        val sheets = chosen.map { ceremony ->
            AwardCeremonyLogic.sheet(
                eventName = event.name,
                eventDate = eventDate,
                eventLocation = event.location,
                competitionIdentifier = ceremony.choice.competitionIdentifier,
                competitionShortName = ceremony.choice.competitionShortName,
                competitionName = ceremony.choice.competitionName,
                ratingCategoryName = ceremony.choice.ratingCategoryName,
                entries = ceremony.entries,
            )
        }

        KIO.ok(
            ApiResponse.File(
                name = "siegerehrung_${event.name}.pdf",
                // Der Bogen ist seit der Verallgemeinerung ein Preset der Ergebnisliste - dass hier
                // dasselbe Blatt herauskommt wie vor dem Umbau, sichern die PDF-Tests zu.
                bytes = AwardCeremonyPdf.render(sheets, ResultListOptions.ceremony),
            )
        )
    }

    // Ohne Sekunden: der Aushang wird minutenweise erneuert, nicht sekundenweise - und zwei
    // Ausdrucke derselben Minute sind auch fachlich derselbe Stand.
    private val standFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY)

    /**
     * Die Ergebnisliste zum Aushängen - dieselbe Datenbasis wie der Siegerehrungsbogen, aber mit
     * wählbaren Bestandteilen: mit oder ohne Schnitt beim Podium, je Wertung ein Abschnitt oder
     * das Gesamtfeld, Mannschaften und Zeiten zuschaltbar, Schriftgrad Aushang oder Siegerehrung.
     *
     * Gedruckt werden ausschließlich bestätigte Platzierungen - exakt die Regel des Bogens:
     * abgemeldete, ausgeschiedene und disqualifizierte Boote fallen heraus, ungewertete ebenso.
     *
     * Jedes Blatt trägt eine Fußzeile mit Veranstaltung und „Stand: …": am Brett hängen erfahrungs-
     * gemäß mehrere Generationen desselben Aushangs, und nur der Zeitstempel sagt, welche gilt.
     */
    fun resultList(
        eventId: UUID,
        competitionId: UUID?,
        options: ResultListOptions,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        // Steht bewusst vor allem anderen: siehe AwardCeremonyError.IsChallengeEvent.
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { AwardCeremonyError.IsChallengeEvent }

        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }
        val eventDays = !EventDayRepo.getByEvent(eventId).orDie()
        val eventDate = AwardCertificateLogic.formatEventDate(eventDays.map { it.date })

        val competitions = (!CompetitionRepo.getByEvent(eventId).orDie())
            .sortedWith(lexiNumberComp { it.identifier })
        val selected = if (competitionId == null) competitions else {
            !KIO.failOn(competitions.none { it.id == competitionId }) {
                AwardCeremonyError.CompetitionNotInEvent
            }
            competitions.filter { it.id == competitionId }
        }

        val maxRank = if (options.podiumOnly) AwardCeremonyLogic.MAX_RANK else null

        val sheets = !selected.traverse { competition ->
            candidatesOf(competition).map { candidates ->
                // Ohne Trennung nach Wertung wird das Gesamtfeld als *ein* Abschnitt gezählt -
                // dieselbe Zählregel (Gleichstände halten den Platz, danach reißt eine Lücke),
                // nur eben über alle Boote des Rennens.
                val sections = RatingCategoryRanking.groupAndRank(
                    items = candidates,
                    category = { if (options.byRatingCategory) it.ratingCategory else null },
                    place = { it.competitionPlace },
                    tieBreak = { it.startNumber },
                )
                sections.map { section ->
                    AwardCeremonyLogic.sheet(
                        eventName = event.name,
                        eventDate = eventDate,
                        eventLocation = event.location,
                        competitionIdentifier = competition.identifier!!,
                        competitionShortName = competition.shortName,
                        competitionName = competition.name!!,
                        ratingCategoryName = section.category?.name,
                        entries = section.entries,
                        maxRank = maxRank,
                    )
                }
            }
        }.map { it.flatten() }
            // Ein Abschnitt ohne einen einzigen bestätigten Platz ergäbe ein Blatt, das nur aus
            // Kopf besteht - am Brett wäre das ein Aushang, der nichts aussagt. Er entfällt.
            .map { all -> all.filter { it.ranks.isNotEmpty() } }

        !KIO.failOn(sheets.isEmpty()) { AwardCeremonyError.NoResults }

        val stamped = options.copy(
            footerLine = "${event.name} — Stand: ${LocalDateTime.now().format(standFormat)}",
        )

        KIO.ok(
            ApiResponse.File(
                name = "ergebnisliste_${event.name}.pdf",
                bytes = AwardCeremonyPdf.render(sheets, stamped),
            )
        )
    }

    /**
     * Die Podien für Siegerehrungs-Kacheln der Boards, tolerant gegenüber veralteter
     * Konfiguration: eine Ehrung zu einem gelöschten Wettkampf oder einer unbekannten
     * Wertung fällt einfach aus der Antwort, statt das ganze Board scheitern zu lassen —
     * ein montierter Bildschirm kann sich nicht beschweren. Challenge-Events haben keine
     * Ehrungen (siehe [AwardCeremonyError.IsChallengeEvent]); dort bleibt die Liste leer.
     *
     * Achtung: das Podium entsteht aus der Platzberechnung, unabhängig von
     * `Event.publicResultsVisibility` — genau wie der gedruckte Bogen. Wer die Kachel
     * konfiguriert, entscheidet damit selbst über den Zeitpunkt der Veröffentlichung.
     */
    fun boardCeremonies(
        eventId: UUID,
        keys: List<AwardCeremonyKeyRequest>,
    ): App<ServiceError, List<BoardCeremonyDto>> = KIO.comprehension {
        if (keys.isEmpty()) {
            return@comprehension KIO.ok(emptyList())
        }
        val isChallenge = !EventService.checkIsChallengeEvent(eventId)
        if (isChallenge) {
            return@comprehension KIO.ok(emptyList())
        }

        // Nur Wettkämpfe, die es (noch) gibt — collect() würde bei einer fremden ID scheitern.
        val existing = (!CompetitionRepo.getByEvent(eventId).orDie()).map { it.id }.toSet()
        val knownKeys = keys.filter { it.competitionId in existing }
        if (knownKeys.isEmpty()) {
            return@comprehension KIO.ok(emptyList())
        }

        val all = !collect(eventId, competitionIds = knownKeys.map { it.competitionId }.distinct())

        KIO.ok(
            knownKeys.mapNotNull { key ->
                all.firstOrNull { it.matches(key) }?.let { ceremony ->
                    BoardCeremonyDto(
                        competitionId = ceremony.choice.competitionId,
                        ratingCategoryId = ceremony.choice.ratingCategoryId,
                        competitionIdentifier = ceremony.choice.competitionIdentifier,
                        competitionShortName = ceremony.choice.competitionShortName,
                        competitionName = ceremony.choice.competitionName,
                        ratingCategoryName = ceremony.choice.ratingCategoryName,
                        ranks = AwardCeremonyLogic.ranks(ceremony.entries),
                    )
                }
            }
        )
    }

    /**
     * Alle Ehrungen der Veranstaltung, in der Reihenfolge der Rennnummern und darin nach Wertung.
     *
     * [competitionIds] `null` heißt „alle Wettkämpfe". Ist die Liste gesetzt, muss jede ID zur
     * Veranstaltung gehören: eine fremde ID stillschweigend zu übergehen hieße, ein Blatt weniger
     * zu drucken, als bestellt wurde.
     */
    private fun collect(
        eventId: UUID,
        competitionIds: List<UUID>?,
    ): App<ServiceError, List<Ceremony>> = KIO.comprehension {
        val competitions = (!CompetitionRepo.getByEvent(eventId).orDie())
            .sortedWith(lexiNumberComp { it.identifier })

        val selected = if (competitionIds == null) competitions else {
            !KIO.failOn(competitionIds.any { id -> competitions.none { it.id == id } }) {
                AwardCeremonyError.CompetitionNotInEvent
            }
            competitions.filter { it.id in competitionIds }
        }

        selected.traverse { ceremoniesOf(it) }.map { it.flatten() }
    }

    /**
     * Die bestätigten Ergebnisse eines Wettkampfs, wie sie in Bogen und Ergebnisliste eingehen -
     * die eine Ergebnisregel für beide Formen: abgemeldete, ausgeschiedene und disqualifizierte
     * Boote bekommen von der Platzberechnung der Vollständigkeit halber einen Platz, gedruckt
     * werden sie nicht (dieselbe Regel wie im Urkundengenerator).
     */
    private fun candidatesOf(competition: CompetitionViewRecord): App<ServiceError, List<AwardCeremonyCandidate>> =
        KIO.comprehension {
            val competitionId = competition.id!!

            // Einmal laden, zweimal gebraucht: die Platzberechnung rechnet daran, und der Lauf je
            // Boot wird darin gesucht. Diese Abfrage zieht Runden → Läufe → Teams → Teilnehmer und
            // ist die teuerste des Wettkampfbereichs - bei 40 Rennen wäre der zweite Zug 40
            // zusätzliche Abfragen, bei jedem Öffnen der Auswahl und bei jedem Download.
            val rounds = CompetitionExecutionService.sortRounds(
                !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
            )
            val places = CompetitionExecutionService.computeCompetitionPlaces(rounds)

            KIO.ok(
                places
                    .filterNot { (team, _) -> team.deregistered || team.out || team.failed }
                    .map { (team, place) ->
                        val (roundName, matchName, matchTime) = raceOf(rounds, team.competitionRegistration)

                        AwardCeremonyCandidate(
                            competitionPlace = place,
                            startNumber = team.startNumber,
                            ratingCategory = team.ratingCategory,
                            registeringClubName = team.clubName,
                            teamName = team.registrationName,
                            time = team.timeString,
                            penaltySeconds = team.penaltySeconds,
                            penaltyNote = team.penaltyNote,
                            roundName = roundName,
                            matchName = matchName,
                            matchTime = matchTime,
                            participants = team.participants.map {
                                AwardCeremonyCandidateParticipant(
                                    firstName = it.firstName,
                                    lastName = it.lastName,
                                    year = it.year,
                                    role = it.namedParticipantName,
                                    external = it.external,
                                    externalClubName = it.externalClubName,
                                    ownClubName = it.clubName,
                                )
                            },
                        )
                    }
            )
        }

    private fun ceremoniesOf(competition: CompetitionViewRecord): App<ServiceError, List<Ceremony>> =
        KIO.comprehension {
            val competitionId = competition.id!!
            val candidates = !candidatesOf(competition)

            // Dieselbe Rechnung wie auf der öffentlichen Ergebnisseite, im Schiedsrichter-Dashboard
            // und im Ergebnis-PDF - samt der Reihenfolge der Abschnitte aus der gepflegten
            // sortOrder. Sie wird hier bewusst nicht mehr angetastet: die Sprecherin liest die
            // Wertungen in derselben Folge, in der die Ergebnisse hängen.
            val sections = RatingCategoryRanking.groupAndRank(
                items = candidates,
                category = { it.ratingCategory },
                place = { it.competitionPlace },
                tieBreak = { it.startNumber },
            )

            KIO.ok(
                sections.map { section ->
                    Ceremony(
                        choice = AwardCeremonyChoiceDto(
                            competitionId = competitionId,
                            competitionIdentifier = competition.identifier!!,
                            competitionShortName = competition.shortName,
                            competitionName = competition.name!!,
                            ratingCategoryId = section.category?.id,
                            ratingCategoryName = section.category?.name,
                            // Nicht die Größe der Gruppe: gedruckt wird nur bis Rang drei, und die
                            // Auswahl soll die Zahl der Blöcke nennen, die daraus wirklich entstehen.
                            awardedTeams = AwardCeremonyLogic.ranks(section.entries).size,
                        ),
                        entries = section.entries,
                    )
                }
            )
        }

    /** Runde, Lauf und Zeitpunkt des Laufs, in dem der Platz dieses Bootes entstanden ist. */
    private fun raceOf(
        rounds: List<CompetitionSetupRoundWithMatches>,
        registrationId: UUID,
    ): Triple<String?, String?, LocalDateTime?> {
        // Rückwärts, weil der Platz in der letzten Runde entsteht, in der die Meldung vorkommt.
        val found = rounds.asReversed().firstNotNullOfOrNull { round ->
            round.matches
                .firstOrNull { match -> match.teams.any { it.competitionRegistration == registrationId } }
                ?.let { round to it }
        } ?: return Triple(null, null, null)

        val (round, match) = found
        val name = round.setupMatches.firstOrNull { it.id == match.competitionSetupMatch }?.name
        // Der tatsächliche Start geht dem geplanten vor: auf dem Blatt steht, wann gefahren wurde.
        return Triple(round.setupRoundName, name, match.startedAt ?: match.startTime)
    }

    /**
     * Die Einheit einer Ehrung ist (Wettkampf, Wertung) - `null` als Wertung inbegriffen.
     *
     * Verglichen wird die Id der Wertung, nicht ihr Name: zwei gleichnamige Kategorien wären sonst
     * ununterscheidbar, und eine Auswahl träfe beide Blätter oder das falsche.
     */
    private fun Ceremony.matches(key: AwardCeremonyKeyRequest): Boolean =
        choice.competitionId == key.competitionId && choice.ratingCategoryId == key.ratingCategoryId
}
