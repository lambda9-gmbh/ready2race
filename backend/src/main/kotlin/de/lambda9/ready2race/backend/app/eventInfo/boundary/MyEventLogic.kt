package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.event.entity.PublicResultsVisibility
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventMatchDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventResultDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventTeamMemberDto
import java.time.LocalDateTime
import java.util.UUID

/**
 * Reine Aufteilungs- und Sortierlogik des persönlichen Dashboards: aus einer flachen Liste
 * der eigenen Läufe werden "läuft gerade", "kommt noch" und "Ergebnis".
 *
 * Die Sichtbarkeitsregel für Ergebnisse und die Ableitung des Startzustands stammen
 * unverändert aus [AthleteBoardLogic]. Das ist Absicht und keine Bequemlichkeit: erschiene
 * ein Ergebnis hier früher als auf der Athleten-Anzeige, stünde dasselbe Rennen auf zwei
 * Bildschirmen unterschiedlich da — und der gezeigte Wert kann sich durch eine später
 * eintreffende Zeitstrafe noch ändern.
 */
object MyEventLogic {

    /** Gleiche Frist wie [AthleteBoardLogic.CACHE_TTL_SECONDS], siehe Klassenkommentar. */
    const val PARTICIPANT_CACHE_TTL_SECONDS = AthleteBoardLogic.CACHE_TTL_SECONDS

    /**
     * Ein Lauf der Person, wie ihn die Datenbank liefert — vor der Einordnung in
     * laufend/kommend/Ergebnis.
     */
    data class RawMatch(
        val matchId: UUID,
        val competitionName: String,
        val categoryName: String?,
        val roundName: String?,
        val matchName: String?,
        val startTime: LocalDateTime?,
        val actualStartTime: LocalDateTime?,
        val finishedAt: LocalDateTime?,
        val allTeamsScored: Boolean,
        val currentlyRunning: Boolean,
        val lane: Int?,
        val teamName: String?,
        val clubName: String?,
        val teamMembers: List<MyEventTeamMemberDto>,
        val place: Int?,
        val timeString: String?,
        val penaltySeconds: Int?,
        val penaltyNote: String?,
        val failed: Boolean,
        val failedReason: String?,
        val deregistered: Boolean,
        val deregisteredReason: String?,
    )

    data class Split(
        val running: List<MyEventMatchDto>,
        val upcoming: List<MyEventMatchDto>,
        val results: List<MyEventResultDto>,
    )

    fun split(
        entries: List<RawMatch>,
        now: LocalDateTime,
        visibility: PublicResultsVisibility,
        showCountdown: Boolean,
    ): Split {
        val (finished, open) = entries.partition {
            AthleteBoardLogic.isPublicResult(it.finishedAt, it.allTeamsScored, visibility)
        }
        val (running, upcoming) = open.partition { it.currentlyRunning }

        return Split(
            running = running
                .sortedWith(compareBy(nullsLast()) { it.startTime })
                .map { it.toMatchDto(now, showCountdown) },
            upcoming = upcoming
                .sortedWith(compareBy(nullsLast()) { it.startTime })
                .map { it.toMatchDto(now, showCountdown) },
            // Neuestes zuerst: nach dem Rennen interessiert das eigene letzte Ergebnis,
            // nicht das vom Vormittag.
            results = finished
                .sortedWith(compareByDescending(nullsLast()) { it.actualStartTime ?: it.startTime })
                .map { it.toResultDto() },
        )
    }

    private fun RawMatch.toMatchDto(now: LocalDateTime, showCountdown: Boolean) = MyEventMatchDto(
        matchId = matchId,
        competitionName = competitionName,
        categoryName = categoryName,
        roundName = roundName,
        matchName = matchName,
        startTime = startTime,
        actualStartTime = actualStartTime,
        startState = AthleteBoardLogic.startState(startTime, now, showCountdown),
        lane = lane,
        teamName = teamName,
        clubName = clubName,
        teamMembers = teamMembers,
    )

    private fun RawMatch.toResultDto() = MyEventResultDto(
        matchId = matchId,
        competitionName = competitionName,
        categoryName = categoryName,
        roundName = roundName,
        matchName = matchName,
        startTime = startTime,
        actualStartTime = actualStartTime,
        place = place,
        timeString = timeString,
        penaltySeconds = penaltySeconds,
        penaltyNote = penaltyNote,
        failed = failed,
        failedReason = failedReason,
        deregistered = deregistered,
        deregisteredReason = deregisteredReason,
    )
}
