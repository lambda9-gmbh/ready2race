package de.lambda9.ready2race.backend.app.eventInfo.control

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.lambda9.ready2race.backend.app.eventInfo.boundary.AthleteBoardLogic
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardMatch
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardParticipant
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardResult
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardResultTeam
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardTeam
import de.lambda9.ready2race.backend.app.eventInfo.entity.BoardConfig
import de.lambda9.ready2race.backend.app.eventInfo.entity.BoardDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.BoardNameDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.BoardRequest
import de.lambda9.ready2race.backend.app.eventInfo.entity.LatestMatchResultInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.LiveMatchInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.RunningMatchInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.RunningMatchTeamInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingCompetitionMatchInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingMatchParticipantInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingMatchTeamInfo
import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardLogic
import de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchStatusLogic
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusTeam
import de.lambda9.ready2race.backend.database.generated.tables.records.BoardRecord
import org.jooq.JSONB
import java.time.LocalDateTime
import java.util.*

/**
 * Ein eigener Mapper mit Kotlin-Modul: [BoardConfig] ist eine Kotlin-Datenklasse mit
 * Vorgabewerten, die der nackte [ObjectMapper] der Nachbar-Konvertierungen nicht
 * konstruieren kann. NON_NULL, damit die Alt-Lesart `layout` nie als `null` in neue
 * JSONB-Stände geschrieben wird.
 */
private val boardConfigMapper = ObjectMapper()
    .registerKotlinModule()
    .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

/**
 * Konfigurationen der ersten Board-Fassung (festes `layout` statt `columns`) werden beim
 * Lesen normalisiert: nach außen trägt jede Antwort `columns`, `layout` verschwindet.
 * Gespeichert wird die neue Form erst mit dem nächsten Speichern des Editors.
 */
private fun BoardConfig.normalized(): BoardConfig =
    if (columns != null) this
    else copy(layout = null, columns = resolvedColumns())

fun BoardRecord.toDto() = BoardDto(
    id = id,
    eventId = eventId,
    name = name,
    config = boardConfigMapper.readValue(config.data(), BoardConfig::class.java).normalized(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BoardRecord.toNameDto() = BoardNameDto(
    id = id,
    name = name,
)

fun BoardConfig.toJsonb(): JSONB = JSONB.jsonb(boardConfigMapper.writeValueAsString(this))

fun BoardRequest.toRecord(eventId: UUID): BoardRecord {
    val now = LocalDateTime.now()
    return BoardRecord(
        id = UUID.randomUUID(),
        eventId = eventId,
        name = name,
        config = config.toJsonb(),
        createdAt = now,
        updatedAt = now,
    )
}

private fun participantName(firstName: String, lastName: String) = "$firstName $lastName"

fun UpcomingMatchParticipantInfo.toAthleteBoardParticipant() = AthleteBoardParticipant(
    name = participantName(firstName, lastName),
    role = namedRole,
)

/**
 * Die Vereinskette der Athleten gewinnt; der meldende Verein tritt nur ein, wenn zum Boot noch
 * gar keine Crew erfasst ist. Die Auflösung passiert hier statt in jeder Ansicht.
 */
private fun clubsOrRegistering(chain: String?, registeringClubName: String?) =
    chain ?: registeringClubName

fun RunningMatchTeamInfo.toAthleteBoardTeam() = AthleteBoardTeam(
    startNumber = startNumber,
    teamNumber = teamNumber,
    clubsShort = clubsOrRegistering(clubsShort, clubName),
    clubsFull = clubsOrRegistering(clubsFull, clubName),
    teamName = teamName,
    participants = participants.map { it.toAthleteBoardParticipant() },
    // Teilergebnis: gefüllt, sobald die Zeitnahme dieses Boot gewertet hat - der Lauf läuft
    // dabei weiter, bis die Organisation ihn beendet.
    place = currentPosition,
    timeString = timeString,
    penaltySeconds = penaltySeconds,
    penaltyNote = penaltyNote,
    failed = failed,
    failedReason = failedReason,
)

fun UpcomingMatchTeamInfo.toAthleteBoardTeam() = AthleteBoardTeam(
    startNumber = startNumber,
    teamNumber = teamNumber,
    clubsShort = clubsOrRegistering(clubsShort, clubName),
    clubsFull = clubsOrRegistering(clubsFull, clubName),
    teamName = teamName,
    participants = participants.map { it.toAthleteBoardParticipant() },
)

fun RunningMatchInfo.toAthleteBoardMatch(now: LocalDateTime, showCountdown: Boolean) =
    AthleteBoardMatch(
        matchId = matchId,
        competitionName = competitionName,
        competitionShortName = competitionShortName,
        categoryName = categoryName,
        roundName = roundName,
        matchName = matchName,
        startTime = startTime,
        actualStartTime = startedAt,
        // Dieselbe Ableitung wie im Schiedsrichter-Dashboard, statt einer zweiten hier: die
        // Karte las bis zum 09.08.2026 nur "actualStartTime gesetzt?" und war damit die einzige
        // Oberfläche mit eigener Wahrheit. finishedAt und skipped sind hier immer aus dem Spiel -
        // dieser Block führt ausschließlich aktivierte Läufe, und Beenden nimmt die Aktivierung
        // zurück.
        state = LiveDashboardLogic.deriveMatchState(
            activatedAt = activatedAt,
            startedAt = startedAt,
            startTime = startTime,
            finishedAt = null,
            teamResults = teams.map {
                LiveDashboardLogic.teamHasResult(it.currentPosition, it.failed, deregistered = false)
            },
        ),
        startState = AthleteBoardLogic.startState(startTime, now, showCountdown),
        teams = teams.map { it.toAthleteBoardTeam() },
    )

fun UpcomingCompetitionMatchInfo.toAthleteBoardMatch(now: LocalDateTime, showCountdown: Boolean) =
    AthleteBoardMatch(
        matchId = matchId,
        competitionName = competitionName,
        competitionShortName = competitionShortName,
        categoryName = categoryName,
        roundName = roundName,
        matchName = matchName,
        startTime = scheduledStartTime,
        // Der Block führt ausschließlich nicht aktivierte Läufe; die Ableitung entscheidet damit
        // nur noch zwischen abgesagt, ungeplant und anstehend. Sie steht trotzdem hier und nicht
        // als eigenes `if`, damit die Anzeige dieselbe Aufzählung liest wie alle anderen.
        state = LiveDashboardLogic.deriveMatchState(
            activatedAt = null,
            startedAt = null,
            startTime = scheduledStartTime,
            finishedAt = null,
            teamResults = emptyList(),
            skipped = cancelled,
        ),
        startState = AthleteBoardLogic.startState(scheduledStartTime, now, showCountdown),
        teams = teams.map { it.toAthleteBoardTeam() },
        pendingRound = pendingRound,
        name = name,
        cancelled = cancelled,
    )

fun LatestMatchResultInfo.toAthleteBoardResult() = AthleteBoardResult(
    matchId = matchId,
    competitionName = competitionName,
    competitionShortName = competitionShortName,
    categoryName = categoryName,
    roundName = roundName,
    matchName = matchName,
    startTime = startTime,
    actualStartTime = startedAt,
    // Abgemeldete Mannschaften bleiben in der Liste, aber ausdrücklich als abgemeldet
    // gekennzeichnet: ohne Platz und ohne Zeit sahen sie früher wie ein Darstellungsfehler aus,
    // ganz weggelassen ließen sie die Besatzung nach ihrem Boot suchen.
    teams = teams.map {
        AthleteBoardResultTeam(
            place = it.place,
            ratingCategory = it.ratingCategory,
            categoryPlace = it.categoryPlace,
            startNumber = it.startNumber,
            teamNumber = it.teamNumber,
            clubsShort = clubsOrRegistering(it.clubsShort, it.clubName),
            clubsFull = clubsOrRegistering(it.clubsFull, it.clubName),
            teamName = it.teamName,
            timeString = it.timeString,
            penaltySeconds = it.penaltySeconds,
            penaltyNote = it.penaltyNote,
            failed = it.failed,
            failedReason = it.failedReason,
            deregistered = it.deregistered,
            deregisteredReason = it.deregisteredReason,
        )
    },
)

/**
 * Ein anstehendes Boot hat noch kein Ergebnis - die Quellabfrage
 * (`CompetitionMatchTeamRepo.getTeamsForUpcomingMatch`) fragt Platz und Zeit gar nicht erst ab.
 * Die Ergebnisfelder bleiben deshalb leer, statt aus einer zweiten Abfrage gefüllt zu werden:
 * genau daran hängt, dass der ANSTEHENDE Zweig der Live-Liste keine Ergebnisse veröffentlichen
 * kann, die `PublicResultsVisibility` zurückhalten soll.
 *
 * Das gilt ausdrücklich NICHT für den aktivierten Zweig ([RunningMatchInfo.toLiveMatchInfo]
 * unten): der reicht die Teilergebnisse eines laufenden Laufs unverändert durch - genau wie der
 * vorbestehende Endpoint `/running-matches`, den dieser Tab schon vor dem 09.08.2026 abrief. Das
 * ist gewollt und keine Aufweichung: ein Boot, das die Zeitnahme schon während des Laufs wertet,
 * durfte diesen Tab schon immer erreichen - Live-Zwischenzeiten sind kein zurückgehaltenes
 * Endergebnis.
 */
fun UpcomingMatchTeamInfo.toRunningMatchTeamInfo() = RunningMatchTeamInfo(
    teamId = teamId,
    teamName = teamName,
    teamNumber = teamNumber,
    startNumber = startNumber,
    clubName = clubName,
    clubsShort = clubsShort,
    clubsFull = clubsFull,
    currentScore = null,
    currentPosition = null,
    timeString = null,
    penaltySeconds = null,
    penaltyNote = null,
    failed = false,
    failedReason = null,
    participants = participants,
)

/**
 * Ein aktivierter Lauf für die öffentliche Live-Liste. `finishedAt` und `skipped` sind hier
 * immer aus dem Spiel: die Quellabfrage führt ausschließlich Läufe mit `activated_at`, und
 * Beenden nimmt die Aktivierung zurück. Der Zustand entsteht trotzdem über
 * [MatchStatusLogic.matchStatus] statt aus einem `if` - es gibt genau eine Ableitung.
 */
fun RunningMatchInfo.toLiveMatchInfo() = LiveMatchInfo(
    matchId = matchId,
    competitionId = competitionId,
    competitionName = competitionName,
    categoryName = categoryName,
    roundName = roundName,
    matchName = matchName,
    startTime = startTime,
    status = MatchStatusLogic.matchStatus(
        activatedAt = activatedAt,
        startTime = startTime,
        startedAt = startedAt,
        finishedAt = null,
        skipped = false,
        teams = teams.map {
            MatchStatusTeam(place = it.currentPosition, failed = it.failed, deregistered = false)
        },
    ),
    executionOrder = executionOrder,
    teams = teams,
)

/**
 * Ein anstehender Lauf für die öffentliche Live-Liste. Die Quellabfrage schließt aktivierte,
 * beendete und vollständig gewertete Läufe aus; die Ableitung entscheidet damit nur noch
 * zwischen abgesagt, ungeplant und anstehend. Sie steht trotzdem hier und nicht als eigenes `if`,
 * damit die Anzeige dieselbe Aufzählung liest wie jede andere Oberfläche.
 *
 * Alle Boote gehen als „noch offen" in die Ableitung - für einen anstehenden Lauf liegt kein
 * Ergebnis vor, und die Quellabfrage könnte auch keins liefern.
 */
fun UpcomingCompetitionMatchInfo.toLiveMatchInfo() = LiveMatchInfo(
    matchId = matchId,
    competitionId = competitionId,
    competitionName = competitionName,
    categoryName = categoryName,
    roundName = roundName,
    matchName = matchName,
    startTime = scheduledStartTime,
    status = MatchStatusLogic.matchStatus(
        activatedAt = null,
        startTime = scheduledStartTime,
        startedAt = null,
        finishedAt = null,
        skipped = cancelled,
        teams = teams.map { MatchStatusTeam(place = null, failed = false, deregistered = false) },
    ),
    executionOrder = executionOrder,
    cancelled = cancelled,
    pendingRound = pendingRound,
    name = name,
    teams = teams.map { it.toRunningMatchTeamInfo() },
)