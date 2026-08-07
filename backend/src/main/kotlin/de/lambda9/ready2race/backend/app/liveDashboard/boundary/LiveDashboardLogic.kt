package de.lambda9.ready2race.backend.app.liveDashboard.boundary

import de.lambda9.ready2race.backend.app.liveDashboard.entity.CheckSeverity
import de.lambda9.ready2race.backend.app.liveDashboard.entity.CheckSeverityConfig
import de.lambda9.ready2race.backend.app.liveDashboard.entity.CheckSeverityEntryDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.CheckSeverityKey
import de.lambda9.ready2race.backend.app.liveDashboard.entity.CheckType
import de.lambda9.ready2race.backend.app.liveDashboard.entity.EffectiveSeverity
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardInvoiceState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardMatchDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardMatchState
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardScope
import de.lambda9.ready2race.backend.app.liveDashboard.entity.TimeCheckDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.TimeCheckStatus
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
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
    /**
     * Wann ist die Mannschaft aufs Wasser gegangen? Ein Boot gilt als "auf dem Wasser", wenn
     * JEDE bekannte Person der Crew zuletzt ausgecheckt ist (letzter Scan = EXIT am Steg) -
     * dann zählt der späteste dieser Scans als Ablegezeit. Fehlt auch nur ein Scan oder ist
     * jemand wieder eingecheckt, ist das Boot nicht (mehr) vollständig draußen -> null.
     * Ohne bekannte Crew lässt sich nichts belegen -> ebenfalls null; die Anzeige behandelt
     * null bei aktivem Lauf als Fehler, denn genau dann muss das Boot draußen sein.
     *
     * [lastScans] enthält je Crew-Mitglied den letzten Scan (scanType zu Zeitpunkt) oder null,
     * wenn die Person nie gescannt wurde.
     */
    fun teamOnWaterAt(lastScans: List<Pair<String, LocalDateTime>?>): LocalDateTime? =
        if (lastScans.isNotEmpty() && lastScans.all { it?.first == ParticipantScanType.EXIT.name }) {
            lastScans.maxOf { it!!.second }
        } else {
            null
        }

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

    fun requirementApplies(
        assignedNamedParticipants: List<UUID?>,
        participantNamedParticipantId: UUID?,
    ): Boolean = assignedNamedParticipants.any { it == null } ||
        (participantNamedParticipantId != null && assignedNamedParticipants.contains(participantNamedParticipantId))

    /**
     * Der eingebaute Standard, wenn für einen Wettkampf nichts eingestellt ist. Er ist mit Absicht
     * genau das Verhalten vor dieser Einstellmöglichkeit: dadurch braucht die Migration keinen
     * Datenschritt, und ein neu angelegter Wettkampf ist ohne Pflege richtig eingestellt.
     */
    fun defaultSeverity(checkType: CheckType, optional: Boolean): CheckSeverity = when (checkType) {
        CheckType.INVOICE_OPEN -> CheckSeverity.CRITICAL
        CheckType.NOT_ON_WATER -> CheckSeverity.CRITICAL
        CheckType.REQUIREMENT -> if (optional) CheckSeverity.OK else CheckSeverity.CRITICAL
        CheckType.REQUIREMENT_TIME_WINDOW -> CheckSeverity.WARNING
    }

    /**
     * Eine erfüllte Prüfung ist immer [EffectiveSeverity.OK] - der Schweregrad beschreibt nur, was
     * ihr Fehlen bedeutet. Die Stufe [CheckSeverity.OK] wird dabei zu [EffectiveSeverity.NEUTRAL]
     * und nicht zu OK: sonst sähe "offen, wird heute nicht geahndet" aus wie "bezahlt".
     */
    fun effectiveSeverity(fulfilled: Boolean, configured: CheckSeverity): EffectiveSeverity =
        if (fulfilled) {
            EffectiveSeverity.OK
        } else when (configured) {
            CheckSeverity.OK -> EffectiveSeverity.NEUTRAL
            CheckSeverity.WARNING -> EffectiveSeverity.WARNING
            CheckSeverity.CRITICAL -> EffectiveSeverity.CRITICAL
        }

    /** Nutzt die natürliche Ordnung von [EffectiveSeverity]; leer heißt "nichts zu sagen". */
    fun worstSeverity(severities: List<EffectiveSeverity>): EffectiveSeverity =
        severities.maxOrNull() ?: EffectiveSeverity.NEUTRAL

    /**
     * Eine Teilnahmebedingung trägt zwei Prüfungen: ob sie abgehakt ist und ob das rechtzeitig
     * geschah. Die Anzeige hat aber nur ein Symbol je Bedingung - also gilt die schlechtere.
     * Ist sie nicht abgehakt, sagt das Zeitfenster ohnehin nichts.
     */
    fun requirementSeverity(
        checked: Boolean,
        timeCheckStatus: TimeCheckStatus?,
        missingSeverity: CheckSeverity,
        timeWindowSeverity: CheckSeverity,
    ): EffectiveSeverity {
        val missing = effectiveSeverity(checked, missingSeverity)
        val window = if (
            timeCheckStatus == TimeCheckStatus.LATE || timeCheckStatus == TimeCheckStatus.TOO_EARLY
        ) {
            effectiveSeverity(false, timeWindowSeverity)
        } else {
            EffectiveSeverity.NEUTRAL
        }
        return worstSeverity(listOf(missing, window))
    }

    /**
     * Grün ([EffectiveSeverity.OK]) heißt in der Ampel "geprüft und in Ordnung" und bleibt den
     * Teilnahmebedingungen vorbehalten - nur sie können eine Mannschaft nach Erfüllung wirklich
     * bestätigen. Rechnung ([invoiceSeverity]) und Wasser ([onWaterSeverity]) sind keine
     * Teilnahmebedingungen: sie können die Ampel nur verschlechtern (Rechnung offen / Boot nicht
     * draußen) oder schweigen ([EffectiveSeverity.NEUTRAL]), aber nie verbessern. Das folgt exakt
     * der alten Frontend-Formel (`invoiceState === 'OPEN' ? 'error' : 'neutral'` bzw.
     * `matchActive && !deregistered && !onWaterAt ? 'error' : 'neutral'`) - in beiden Zweigen gab
     * es dort keinen Weg zu `'ok'`. Sonst zeigt eine Regatta ohne jede eingestellte
     * Teilnahmebedingung überall einen grünen Haken, wo vorher ein grauer Kreis stand.
     *
     * [LiveDashboardInvoiceState.NONE] heißt "es gibt keine Rechnung" und ist deshalb keine
     * erfüllte Prüfung, sondern gar keine.
     */
    fun invoiceSeverity(state: LiveDashboardInvoiceState, configured: CheckSeverity): EffectiveSeverity =
        when (state) {
            LiveDashboardInvoiceState.NONE -> EffectiveSeverity.NEUTRAL
            LiveDashboardInvoiceState.PAID -> EffectiveSeverity.NEUTRAL
            LiveDashboardInvoiceState.OPEN -> effectiveSeverity(false, configured)
        }

    /**
     * Ob "auf dem Wasser" für diese Mannschaft gerade überhaupt eine Aussage ist. Alle drei
     * Bedingungen sind nötig:
     *
     * - [matchRunning]: vor dem Start am Steg ist "nicht draußen" der Normalfall, kein Fehler -
     *   erst ein aktiver Lauf macht ein Boot am Steg zur Auffälligkeit.
     * - [checkInOutRequired]: eine Eigenschaft des Wettkampfs, nicht der Mannschaft. Ohne An-/
     *   Abmeldung (z.B. Beachsprint) gibt es kein Auschecken am Steg zu bewerten - dort wäre jedes
     *   Boot für immer "nicht draußen".
     * - `!`[deregistered]: eine abgemeldete Mannschaft fährt nicht mehr; für sie gibt es kein
     *   Wasser mehr zu betreten und keinen Grund, das einzufordern.
     *
     * Fehlt eine der drei, ist die Prüfung nicht anwendbar statt verletzt - deshalb `evaluated`,
     * nicht `onWater`, in [onWaterSeverity].
     */
    fun onWaterApplies(matchRunning: Boolean, checkInOutRequired: Boolean, deregistered: Boolean): Boolean =
        matchRunning && checkInOutRequired && !deregistered

    /**
     * [evaluated] fasst zusammen, wann "auf dem Wasser" überhaupt eine Aussage ist - siehe
     * [onWaterApplies].
     *
     * Ist das Boot auf dem Wasser, ist das - wie bei [invoiceSeverity] beschrieben - keine erfüllte
     * Teilnahmebedingung, sondern der unauffällige Regelfall: [EffectiveSeverity.NEUTRAL], nicht OK.
     * Nur das Fehlen ("nicht draußen, obwohl der Lauf läuft") bekommt den konfigurierten
     * Schweregrad.
     */
    fun onWaterSeverity(evaluated: Boolean, onWater: Boolean, configured: CheckSeverity): EffectiveSeverity =
        if (!evaluated || onWater) EffectiveSeverity.NEUTRAL else effectiveSeverity(false, configured)

    fun teamSeverity(
        requirementSeverities: List<EffectiveSeverity>,
        invoice: EffectiveSeverity,
        onWater: EffectiveSeverity,
    ): EffectiveSeverity = worstSeverity(requirementSeverities + invoice + onWater)

    /**
     * Baut die Konfiguration aus den Datenbankzeilen. Unbekannte Werte werden übergangen statt zu
     * scheitern: die Anzeige am Steg darf nicht ausfallen, weil eine Zeile aus einer neueren
     * Version in der Tabelle steht. Ohne Eintrag greift ohnehin der Standard.
     *
     * [rows] je Zeile: Wettkampf, (Prüfungsart, Bedingung), Schweregrad - alles als Rohwerte.
     */
    fun buildCheckSeverityConfig(
        rows: List<Triple<UUID, Pair<String, UUID?>, String>>,
    ): CheckSeverityConfig = CheckSeverityConfig(
        rows.mapNotNull { (competitionId, check, severity) ->
            val (typeName, requirementId) = check
            val type = CheckType.entries.firstOrNull { it.name == typeName } ?: return@mapNotNull null
            val value = CheckSeverity.entries.firstOrNull { it.name == severity } ?: return@mapNotNull null
            CheckSeverityKey(competitionId, type, requirementId) to value
        }.toMap()
    )

    /**
     * Wählt aus den eingereichten Einträgen, welche gespeichert werden. `replaceForEvent` ersetzt
     * die gesamte Konfiguration einer Veranstaltung - ein Eintrag, den diese Funktion verwirft, ist
     * damit unwiderruflich gelöscht, auch wenn er vorher gültig war.
     *
     * Ein Eintrag ohne [CheckSeverityEntryDto.requirementId] (Rechnung, Wasser) hängt an keiner
     * Teilnahmebedingung und damit an keinem Fremdschlüssel - er bleibt immer erhalten. Ein Eintrag
     * mit Bedingung bleibt erhalten, wenn die Bedingung aktuell zur Veranstaltung gehört
     * ([optionalByRequirement]) ODER wenn er bereits gespeichert war ([persistedRequirementIds]):
     * der Verwaltungsdialog schickt einen gespeicherten Eintrag einer vorübergehend abgemeldeten
     * Bedingung unverändert mit, damit er eine erneute Zuordnung übersteht statt beim nächsten
     * Speichern verloren zu gehen. Verworfen wird nur, was keins von beidem ist - eine erfundene
     * oder veranstaltungsfremde Kennung, die am Fremdschlüssel auf `participant_requirement`
     * scheitern würde (der zeigt auf die globale Tabelle, nicht auf die Veranstaltungszuordnung -
     * eine bloß abgemeldete Bedingung verletzt ihn nicht).
     *
     * Für aktuell zugeordnete Bedingungen entfällt zusätzlich, was dem Standard entspricht (siehe
     * [defaultSeverity]) - die Tabelle bleibt dünn. Für eine abgemeldete, aber gespeicherte
     * Bedingung fehlt das `optional`-Kennzeichen dafür; sie bleibt deshalb ungeprüft erhalten.
     */
    fun entriesToPersist(
        entries: List<CheckSeverityEntryDto>,
        competitionIds: Set<UUID>,
        optionalByRequirement: Map<UUID, Boolean>,
        persistedRequirementIds: Set<UUID>,
    ): List<CheckSeverityEntryDto> = entries
        .filter { it.competitionId in competitionIds }
        .filter { entry ->
            entry.requirementId == null ||
                entry.requirementId in optionalByRequirement ||
                entry.requirementId in persistedRequirementIds
        }
        .filter { entry ->
            val requirementId = entry.requirementId
            val currentlyAssigned = requirementId == null || requirementId in optionalByRequirement
            !currentlyAssigned || entry.severity != defaultSeverity(
                entry.checkType,
                requirementId?.let { optionalByRequirement[it] } == true,
            )
        }
}
