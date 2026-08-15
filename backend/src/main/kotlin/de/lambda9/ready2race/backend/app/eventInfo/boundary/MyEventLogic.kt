package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.event.entity.PublicResultsVisibility
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventMatchDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventResultDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventTeamMemberDto
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementScopeLogic
import java.time.Duration
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

    /**
     * Nachfrist für "kommt noch", übernommen aus [AthleteBoardLogic]: Ein Lauf, dessen Startzeit
     * um mehr als diese Frist verstrichen ist, ohne dass er läuft oder ein Ergebnis trägt
     * (Zeitplanänderung, abgebrochene Runde, vergessener Klick), gilt auf `/board` nicht mehr als
     * anstehend. Bewusst dieselbe Grenze, damit derselbe Lauf nicht auf dem Telefon noch ansteht,
     * während er auf der Wandanzeige längst verschwunden ist.
     */
    private val UPCOMING_GRACE: Duration =
        Duration.ofMinutes(AthleteBoardLogic.DEFAULT_OVERDUE_GRACE_MINUTES.toLong())

    /**
     * Ein Lauf der Person, wie ihn die Datenbank liefert — vor der Einordnung in
     * laufend/kommend/Ergebnis.
     */
    data class RawMatch(
        val matchId: UUID,
        /** Die eigene Meldung in diesem Lauf — wird nur am Ergebnis nach außen gereicht. */
        val teamId: UUID?,
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
            // Überfällige Läufe wandern ans Ende, statt verworfen zu werden. Rein aufsteigend
            // sortiert stünde ein vergessener 09-Uhr-Lauf um 13:00 vor dem tatsächlich nächsten,
            // und die Karte oben ("Dein nächster Lauf") zeigte die falsche Uhrzeit. Weglassen
            // wäre die andere schlechte Antwort: dem eigenen Menschen soll sein Lauf nicht
            // kommentarlos abhandenkommen — anders als auf der Wandanzeige, die nur den Betrieb
            // zeigt. Läufe ganz ohne Startzeit gelten weiter als anstehend und stehen deshalb
            // am Ende der vorderen Gruppe.
            upcoming = upcoming
                .sortedWith(
                    compareBy<RawMatch> {
                        !AthleteBoardLogic.isStillUpcoming(it.startTime, now, UPCOMING_GRACE)
                    }.thenBy(nullsLast<LocalDateTime>()) { it.startTime }
                )
                .map { it.toMatchDto(now, showCountdown) },
            // Neuestes zuerst: nach dem Rennen interessiert das eigene letzte Ergebnis,
            // nicht das vom Vormittag. Achtung: compareByDescending vertauscht intern die
            // Vergleichsrichtung, wodurch sich auch die Nullwert-Platzierung umkehrt — deshalb
            // steht hier nullsFirst() (nicht nullsLast()), damit ein Eintrag ganz ohne
            // Zeitangabe (z. B. vor dem Start abgemeldet) am Ende landet statt fälschlich oben.
            results = finished
                .sortedWith(compareByDescending(nullsFirst()) { it.actualStartTime ?: it.startTime })
                .map { it.toResultDto() },
        )
    }

    /**
     * Der erste künftige Start der Person: die früheste geplante Startzeit unter den kommenden
     * Läufen, echt nach [now]. Abgemeldete Boote zählen nicht — dieser Start findet für die
     * Person nicht statt, ein daraus gerechnetes Erledigungsfenster schickte sie zur falschen
     * Zeit an die Meldestelle. Überfällige Läufe (Startzeit verstrichen, Nachfrist läuft noch)
     * zählen ebenfalls nicht: "künftig" heißt hier wörtlich in der Zukunft.
     */
    fun firstFutureStart(upcoming: List<MyEventMatchDto>, now: LocalDateTime): LocalDateTime? =
        upcoming.asSequence()
            .filter { !it.deregistered }
            .mapNotNull { it.startTime }
            .filter { it.isAfter(now) }
            .minOrNull()

    /**
     * Eine Grenze des Erledigungsfensters einer Bedingung: [minutesBefore] Minuten vor dem
     * ersten künftigen Start. null, sobald eine der beiden Größen fehlt — ein halbes Fenster
     * gibt es, ein erfundenes nicht.
     *
     * Gerechnet wird seit dem 14.08.2026 in [RequirementScopeLogic.windowBound], das den
     * Bezugszeitpunkt als Argument nimmt statt ihn festzulegen. Der Grund steht dort: Bei
     * einer Bedingung, die je Wettkampf gilt, muss das Fenster gegen den Lauf gelten, um den
     * es geht — wer an einem Tag zweimal startet, bekäme sonst die Grenzen des falschen
     * Rennens. "Mein Event" reicht unverändert den ersten künftigen Start der Person herein
     * und behält damit sein Verhalten.
     */
    fun checkWindowBound(firstFutureStart: LocalDateTime?, minutesBefore: Int?): LocalDateTime? =
        RequirementScopeLogic.windowBound(firstFutureStart, minutesBefore)

    private fun RawMatch.toMatchDto(now: LocalDateTime, showCountdown: Boolean) = MyEventMatchDto(
        matchId = matchId,
        competitionName = competitionName,
        categoryName = categoryName,
        roundName = roundName,
        matchName = matchName,
        startTime = startTime,
        actualStartTime = actualStartTime,
        // Ein abgemeldetes Boot bekommt keinen Countdown: eine Zahl, die auf einen Start
        // hinunterzählt, den es nicht gibt, schickt jemanden an den Steg.
        startState = AthleteBoardLogic.startState(startTime, now, showCountdown && !deregistered),
        lane = lane,
        teamName = teamName,
        clubName = clubName,
        teamMembers = teamMembers,
        deregistered = deregistered,
        deregisteredReason = deregisteredReason,
    )

    private fun RawMatch.toResultDto() = MyEventResultDto(
        matchId = matchId,
        teamId = teamId,
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
