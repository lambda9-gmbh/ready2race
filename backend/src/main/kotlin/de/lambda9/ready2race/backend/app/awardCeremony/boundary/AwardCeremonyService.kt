package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidate
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidateParticipant
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyChoiceDto
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyError
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyKeyRequest
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySelectionRequest
import de.lambda9.ready2race.backend.app.certificate.boundary.AwardCertificateLogic
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionViewRecord
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.lexiNumberComp
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
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
        val candidates: List<AwardCeremonyCandidate>,
    )

    fun listCeremonies(eventId: UUID): App<ServiceError, ApiResponse.ListDto<AwardCeremonyChoiceDto>> =
        KIO.comprehension {
            // Steht bewusst vor allem anderen: siehe AwardCeremonyError.IsChallengeEvent.
            !EventService.checkIsChallengeEvent(eventId).onTrueFail { AwardCeremonyError.IsChallengeEvent }

            val ceremonies = !collect(eventId, competitionIds = null)
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
                candidates = ceremony.candidates,
            )
        }

        KIO.ok(
            ApiResponse.File(
                name = "siegerehrung_${event.name}.pdf",
                bytes = AwardCeremonyPdf.render(sheets),
            )
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

    private fun ceremoniesOf(competition: CompetitionViewRecord): App<ServiceError, List<Ceremony>> =
        KIO.comprehension {
            val competitionId = competition.id!!

            val places = !CompetitionExecutionService.computeCompetitionPlaces(competitionId)
            val rounds = CompetitionExecutionService.sortRounds(
                !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
            )

            val candidates = places
                // Dieselbe Regel wie im Urkundengenerator: abgemeldete, ausgeschiedene und
                // disqualifizierte Boote bekommen von der Platzberechnung der Vollständigkeit
                // halber einen Platz, geehrt werden sie nicht.
                .filterNot { (team, _) -> team.deregistered || team.out || team.failed }
                .map { (team, place) ->
                    val (roundName, matchName, matchTime) = raceOf(rounds, team.competitionRegistration)

                    AwardCeremonyCandidate(
                        competitionPlace = place,
                        startNumber = team.startNumber,
                        ratingCategoryName = team.ratingCategory,
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
                                role = it.namedParticipantName,
                                external = it.external,
                                externalClubName = it.externalClubName,
                                ownClubName = it.clubName,
                            )
                        },
                    )
                }

            KIO.ok(
                AwardCeremonyLogic.groupByRatingCategory(candidates).map { (category, group) ->
                    Ceremony(
                        choice = AwardCeremonyChoiceDto(
                            competitionId = competitionId,
                            competitionIdentifier = competition.identifier!!,
                            competitionShortName = competition.shortName,
                            competitionName = competition.name!!,
                            ratingCategoryName = category,
                            // Nicht die Größe der Gruppe: gedruckt wird nur bis Rang drei, und die
                            // Auswahl soll die Zahl der Blöcke nennen, die daraus wirklich entstehen.
                            awardedTeams = AwardCeremonyLogic.rank(group).size,
                        ),
                        candidates = group,
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

    /** Die Einheit einer Ehrung ist (Wettkampf, Wertung) - `null` als Wertung inbegriffen. */
    private fun Ceremony.matches(key: AwardCeremonyKeyRequest): Boolean =
        choice.competitionId == key.competitionId && choice.ratingCategoryName == key.ratingCategoryName
}
