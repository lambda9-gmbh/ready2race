package de.lambda9.ready2race.backend.app.liveDashboard.boundary

import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardInvoiceState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardMatchDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardMatchState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardScope
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardRequirementStatusDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardRequirementSummaryDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.TimeCheckDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.TimeCheckStatus
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

object LiveDashboardLogic {

    fun computeTimeCheck(
        startTime: LocalDateTime?,
        checkedAt: LocalDateTime?,
        earliestMinutesBefore: Int?,
        latestMinutesBefore: Int?,
    ): TimeCheckDto? {
        if (earliestMinutesBefore == null && latestMinutesBefore == null) return null
        if (startTime == null) return null
        if (checkedAt == null) return TimeCheckDto(null, TimeCheckStatus.NOT_CHECKED)

        val deltaMinutes = Duration.between(checkedAt, startTime).toMinutes()
        val status = when {
            earliestMinutesBefore != null && deltaMinutes > earliestMinutesBefore -> TimeCheckStatus.TOO_EARLY
            latestMinutesBefore != null && deltaMinutes < latestMinutesBefore -> TimeCheckStatus.LATE
            else -> TimeCheckStatus.OK
        }
        return TimeCheckDto(deltaMinutes, status)
    }

    fun deriveInvoiceState(paidAts: List<LocalDateTime?>): LiveDashboardInvoiceState = when {
        paidAts.isEmpty() -> LiveDashboardInvoiceState.NONE
        paidAts.any { it == null } -> LiveDashboardInvoiceState.OPEN
        else -> LiveDashboardInvoiceState.PAID
    }

    /**
     * Die Reihenfolge der Zweige ist die eigentliche Aussage:
     *
     * 1. [LiveDashboardMatchState.RUNNING] bleibt vorn. Ein aktiver Lauf mit vollständigen
     *    Ergebnissen zeigt weiter "Läuft" und hat den Beenden-Knopf - dort ist nichts kaputt.
     * 2. [LiveDashboardMatchState.FINISHED] heißt ausschließlich `finished_at is not null`, also
     *    "jemand hat beendet". Bis zum 06.08.2026 fiel hier auch "alle gewertet" hinein; genau das
     *    war der Fehler (Testkatalog D15): der Lauf verschwand aus dem Live-Tab und bot
     *    "Lauf aktivieren" statt "Lauf beenden" an.
     * 3. [skipped] kommt aus dem Zeitstrahl-Slot des Laufs (siehe
     *    `EventScheduleLogic.skippedMatchIdOrNull`) und steht bewusst HINTER "läuft" und "beendet":
     *    Was tatsächlich passiert ist, schlägt den zurückgenommenen Plan. Ein abgesagter Lauf, der
     *    trotzdem aktiv ist, zeigt deshalb weiter RUNNING statt zu behaupten, es passiere nichts -
     *    dass dieser Zustand gar nicht erst entsteht, sichert die Schutzregel in
     *    `EventScheduleService.setSlotSkipped`. Aus derselben Überlegung steht SKIPPED VOR
     *    [LiveDashboardMatchState.AWAITING_FINISH]: ein abgesagter Lauf braucht niemanden mehr,
     *    der ihn beendet.
     * 4. [LiveDashboardMatchState.AWAITING_FINISH] trifft damit genau den Fall "nicht aktiv, nicht
     *    beendet, aber vollständig gewertet" - das Büro trägt nach, oder der Lauf wurde
     *    deaktiviert. Der Lauf bleibt sichtbar und wartet auf den Beenden-Klick; der
     *    RaceClocker-Pull meldet nur Daten und beendet nie (Entscheidung vom 04.08.2026).
     *
     * Der Zustand ist reine Anzeige. Die Lauf-Kette hängt unverändert an `finished_at`
     * (`ScheduleChain.decideNext` über `ChainSlot.matchFinished`) und kennt diese Aufzählung nicht.
     */
    fun deriveMatchState(
        currentlyRunning: Boolean,
        startTime: LocalDateTime?,
        finishedAt: LocalDateTime?,
        teamResults: List<Boolean>,
        skipped: Boolean = false,
    ): LiveDashboardMatchState = when {
        currentlyRunning -> LiveDashboardMatchState.RUNNING
        finishedAt != null -> LiveDashboardMatchState.FINISHED
        skipped -> LiveDashboardMatchState.SKIPPED
        teamResults.isNotEmpty() && teamResults.all { it } -> LiveDashboardMatchState.AWAITING_FINISH
        startTime == null -> LiveDashboardMatchState.UNSCHEDULED
        else -> LiveDashboardMatchState.UPCOMING
    }

    /**
     * Abgemeldete Mannschaften brauchen kein Ergebnis — für sie kommt keins mehr. Ohne diesen
     * Fall erreicht ein Lauf mit einer Abmeldung nie den Zustand [LiveDashboardMatchState.FINISHED].
     */
    fun teamHasResult(place: Int?, failed: Boolean, deregistered: Boolean): Boolean =
        deregistered || place != null || failed

    /**
     * Was eine Abfrage im gewünschten Umfang zurückgibt: alles, oder die Läufe, die jetzt eine
     * Handlung verlangen — und wenn es keine gibt, der nächste anstehende. Die Reihenfolge bleibt
     * erhalten; die Läufe kommen bereits nach Startzeit sortiert aus der Datenbank.
     *
     * [LiveDashboardMatchState.AWAITING_FINISH] zählt hier wie
     * [LiveDashboardMatchState.RUNNING] dazu, und das ist der eigentliche Kern der Korrektur:
     * Ohne diesen Zweig bliebe ein vollständig gewerteter, aber nicht beendeter Lauf aus dem
     * Live-Tab verschwunden — genau das Verschwinden, das der neue Zustand beheben soll. Er ist
     * der Lauf, auf dessen Beenden gerade alles wartet.
     */
    fun selectForScope(
        matches: List<LiveDashboardMatchDto>,
        scope: LiveDashboardScope,
    ): List<LiveDashboardMatchDto> = when (scope) {
        LiveDashboardScope.ALL -> matches
        LiveDashboardScope.LIVE -> matches
            .filter {
                it.state == LiveDashboardMatchState.RUNNING ||
                    it.state == LiveDashboardMatchState.AWAITING_FINISH
            }
            .ifEmpty {
                listOfNotNull(matches.firstOrNull { it.state == LiveDashboardMatchState.UPCOMING })
            }
    }

    /**
     * Verdichtet die Bedingungen aller Personen einer Mannschaft auf die Zahlen, aus denen die
     * Liste ihre Ampel ableitet. Die Bedingungen selbst bleiben dem Detail-Dialog vorbehalten.
     */
    fun summarizeRequirements(
        requirements: List<LiveDashboardRequirementStatusDto>,
    ): LiveDashboardRequirementSummaryDto = LiveDashboardRequirementSummaryDto(
        total = requirements.size,
        fulfilled = requirements.count { it.checked },
        missingRequired = requirements.count { !it.checked && !it.optional },
        missingOptional = requirements.count { !it.checked && it.optional },
        timeIssues = requirements.count {
            it.timeCheck?.status == TimeCheckStatus.LATE || it.timeCheck?.status == TimeCheckStatus.TOO_EARLY
        },
    )

    fun requirementApplies(
        assignedNamedParticipants: List<UUID?>,
        participantNamedParticipantId: UUID?,
    ): Boolean = assignedNamedParticipants.any { it == null } ||
        (participantNamedParticipantId != null && assignedNamedParticipants.contains(participantNamedParticipantId))
}
