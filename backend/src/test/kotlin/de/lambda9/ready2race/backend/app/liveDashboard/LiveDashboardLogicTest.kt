package de.lambda9.ready2race.backend.app.liveDashboard

import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardLogic
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
import de.lambda9.ready2race.backend.app.liveDashboard.entity.TimeCheckStatus
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveDashboardLogicTest {

    private val start = LocalDateTime.of(2026, 7, 29, 14, 0)

    // --- teamOnWaterAt ---

    @Test
    fun onWaterWhenWholeCrewCheckedIn() {
        val scans = listOf(
            "ENTRY" to start.minusMinutes(10),
            "ENTRY" to start.minusMinutes(8),
            "ENTRY" to start.minusMinutes(12),
        )
        assertEquals(start.minusMinutes(8), LiveDashboardLogic.teamOnWaterAt(scans))
    }

    @Test
    fun notOnWaterWhenAnyCrewMemberMissingOrCheckedOut() {
        // Eine Person nie gescannt
        assertNull(
            LiveDashboardLogic.teamOnWaterAt(listOf("ENTRY" to start, null))
        )
        // Eine Person wieder ausgecheckt (letzter Scan EXIT) - zurück am Steg
        assertNull(
            LiveDashboardLogic.teamOnWaterAt(listOf("ENTRY" to start, "EXIT" to start.plusMinutes(1)))
        )
    }

    @Test
    fun notOnWaterWithoutKnownCrew() {
        assertNull(LiveDashboardLogic.teamOnWaterAt(emptyList()))
    }

    // --- computeTimeCheck ---

    @Test
    fun noWindowConfiguredYieldsNoTimeCheck() {
        assertNull(LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(30), null, null))
    }

    @Test
    fun noStartTimeYieldsNoTimeCheck() {
        assertNull(LiveDashboardLogic.computeTimeCheck(null, start.minusMinutes(30), 120, 15))
    }

    @Test
    fun missingCheckYieldsNotChecked() {
        val result = LiveDashboardLogic.computeTimeCheck(start, null, 120, 15)!!
        assertEquals(TimeCheckStatus.NOT_CHECKED, result.status)
        assertNull(result.deltaMinutes)
    }

    @Test
    fun checkWithinWindowIsOk() {
        val result = LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(60), 120, 15)!!
        assertEquals(TimeCheckStatus.OK, result.status)
        assertEquals(60L, result.deltaMinutes)
    }

    @Test
    fun boundariesAreInclusive() {
        assertEquals(TimeCheckStatus.OK, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(120), 120, 15)!!.status)
        assertEquals(TimeCheckStatus.OK, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(15), 120, 15)!!.status)
    }

    @Test
    fun checkTooFarBeforeStartIsTooEarly() {
        val result = LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(121), 120, 15)!!
        assertEquals(TimeCheckStatus.TOO_EARLY, result.status)
        assertEquals(121L, result.deltaMinutes)
    }

    @Test
    fun checkTooCloseToStartIsLate() {
        val result = LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(14), 120, 15)!!
        assertEquals(TimeCheckStatus.LATE, result.status)
    }

    @Test
    fun checkAfterStartIsLateWhenLatestConfigured() {
        val result = LiveDashboardLogic.computeTimeCheck(start, start.plusMinutes(5), 120, 15)!!
        assertEquals(TimeCheckStatus.LATE, result.status)
        assertEquals(-5L, result.deltaMinutes)
    }

    @Test
    fun oneSidedEarliestOnlyWindow() {
        assertEquals(TimeCheckStatus.OK, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(5), 120, null)!!.status)
        assertEquals(TimeCheckStatus.TOO_EARLY, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(180), 120, null)!!.status)
    }

    @Test
    fun oneSidedLatestOnlyWindow() {
        assertEquals(TimeCheckStatus.OK, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(180), null, 15)!!.status)
        assertEquals(TimeCheckStatus.LATE, LiveDashboardLogic.computeTimeCheck(start, start.minusMinutes(5), null, 15)!!.status)
    }

    // --- deriveInvoiceState ---

    @Test
    fun noInvoicesIsNone() {
        assertEquals(LiveDashboardInvoiceState.NONE, LiveDashboardLogic.deriveInvoiceState(emptyList()))
    }

    @Test
    fun anyUnpaidInvoiceIsOpen() {
        assertEquals(
            LiveDashboardInvoiceState.OPEN,
            LiveDashboardLogic.deriveInvoiceState(listOf(LocalDateTime.now(), null))
        )
    }

    @Test
    fun allPaidIsPaid() {
        assertEquals(
            LiveDashboardInvoiceState.PAID,
            LiveDashboardLogic.deriveInvoiceState(listOf(LocalDateTime.now(), LocalDateTime.now()))
        )
    }

    // --- deriveMatchState ---

    // Vor der Trennung von Aktivierung und Ist-Start stand an dieser Stelle ein einzelnes `true`.
    // Ein Lauf, der wirklich fährt, trägt jetzt beide Zeitpunkte.
    private val activated = start.minusMinutes(5)
    private val reallyStarted = start.plusMinutes(1)

    @Test
    fun `aktiviert ohne Ist-Start ist PREPARING`() {
        val state = LiveDashboardLogic.deriveMatchState(
            activatedAt = LocalDateTime.of(2026, 8, 14, 10, 0),
            startedAt = null,
            startTime = LocalDateTime.of(2026, 8, 14, 10, 5),
            finishedAt = null,
            teamResults = listOf(false, false),
            skipped = false,
        )
        assertEquals(LiveDashboardMatchState.PREPARING, state)
    }

    @Test
    fun `aktiviert mit Ist-Start ist RUNNING`() {
        val state = LiveDashboardLogic.deriveMatchState(
            activatedAt = LocalDateTime.of(2026, 8, 14, 10, 0),
            startedAt = LocalDateTime.of(2026, 8, 14, 10, 6),
            startTime = LocalDateTime.of(2026, 8, 14, 10, 5),
            finishedAt = null,
            teamResults = listOf(false, false),
            skipped = false,
        )
        assertEquals(LiveDashboardMatchState.RUNNING, state)
    }

    @Test
    fun `ein beendeter Lauf bleibt FINISHED, auch wenn er noch aktiviert waere`() {
        val state = LiveDashboardLogic.deriveMatchState(
            activatedAt = null,
            startedAt = LocalDateTime.of(2026, 8, 14, 10, 6),
            startTime = LocalDateTime.of(2026, 8, 14, 10, 5),
            finishedAt = LocalDateTime.of(2026, 8, 14, 10, 20),
            teamResults = listOf(true, true),
            skipped = false,
        )
        assertEquals(LiveDashboardMatchState.FINISHED, state)
    }

    @Test
    fun activatedWithRealStartWinsOverEverything() {
        assertEquals(
            LiveDashboardMatchState.RUNNING,
            LiveDashboardLogic.deriveMatchState(activated, reallyStarted, null, null, listOf(true, true))
        )
    }

    @Test
    fun allPlacesSetButNobodyFinishedAwaitsFinish() {
        // Testkatalog D15: vollständige Ergebnisse sind KEIN Beenden. Bis zum 06.08.2026 stand
        // hier FINISHED - der Lauf verschwand damit aus dem Live-Tab und bot "Lauf aktivieren"
        // statt "Lauf beenden" an.
        assertEquals(
            LiveDashboardMatchState.AWAITING_FINISH,
            LiveDashboardLogic.deriveMatchState(null, null, start, null, listOf(true, true))
        )
    }

    @Test
    fun activeMatchWithCompleteResultsStaysRunning() {
        // RUNNING steht vor AWAITING_FINISH: ein aktiver Lauf hat den Beenden-Knopf ohnehin.
        assertEquals(
            LiveDashboardMatchState.RUNNING,
            LiveDashboardLogic.deriveMatchState(activated, reallyStarted, start, null, listOf(true, true))
        )
    }

    @Test
    fun finishedStaysFinishedEvenWithCompleteResults() {
        assertEquals(
            LiveDashboardMatchState.FINISHED,
            LiveDashboardLogic.deriveMatchState(null, null, start, start.plusMinutes(9), listOf(true, true))
        )
    }

    @Test
    fun noTeamsIsNeverFinished() {
        assertEquals(
            LiveDashboardMatchState.UPCOMING,
            LiveDashboardLogic.deriveMatchState(null, null, start, null, emptyList())
        )
    }

    @Test
    fun missingStartTimeIsUnscheduled() {
        assertEquals(
            LiveDashboardMatchState.UNSCHEDULED,
            LiveDashboardLogic.deriveMatchState(null, null, null, null, listOf(false, false))
        )
    }

    @Test
    fun startTimeInPastWithoutPlacesIsStillUpcoming() {
        assertEquals(
            LiveDashboardMatchState.UPCOMING,
            LiveDashboardLogic.deriveMatchState(null, null, LocalDateTime.now().minusHours(1), null, listOf(true, false))
        )
    }

    @Test
    fun failedTeamWithoutPlaceCountsAsResult() {
        assertTrue(LiveDashboardLogic.teamHasResult(1, false, false))
        assertTrue(LiveDashboardLogic.teamHasResult(null, true, false))
        assertFalse(LiveDashboardLogic.teamHasResult(null, false, false))
        assertEquals(
            LiveDashboardMatchState.AWAITING_FINISH,
            LiveDashboardLogic.deriveMatchState(
                null,
                null,
                start,
                null,
                listOf(
                    LiveDashboardLogic.teamHasResult(1, false, false),
                    LiveDashboardLogic.teamHasResult(null, true, false),
                ),
            )
        )
    }

    @Test
    fun deregisteredTeamNeedsNoResult() {
        assertTrue(LiveDashboardLogic.teamHasResult(null, false, true))
    }

    @Test
    fun matchWithDeregisteredTeamCanFinish() {
        assertEquals(
            LiveDashboardMatchState.AWAITING_FINISH,
            LiveDashboardLogic.deriveMatchState(
                null,
                null,
                start,
                null,
                listOf(
                    LiveDashboardLogic.teamHasResult(1, false, false),
                    LiveDashboardLogic.teamHasResult(null, false, true),
                ),
            )
        )
    }

    @Test
    fun finishedAtBeatsIncompleteResults() {
        // Ohne Ergebnisse beendet: bisher fiel das auf UPCOMING zurück (A4-Loch).
        assertEquals(
            LiveDashboardMatchState.FINISHED,
            LiveDashboardLogic.deriveMatchState(null, null, start, start.plusMinutes(9), listOf(false, false)),
        )
    }

    @Test
    fun cancelledMatchWithCompleteResultsStaysSkipped() {
        // SKIPPED steht vor AWAITING_FINISH: einen abgesagten Lauf muss niemand mehr beenden.
        assertEquals(
            LiveDashboardMatchState.SKIPPED,
            LiveDashboardLogic.deriveMatchState(null, null, start, null, listOf(true, true), skipped = true),
        )
    }

    @Test
    fun cancelledSlotMarksItsMatchAsSkipped() {
        // Befund A fürs Schiedsrichter-Dashboard: kennzeichnen statt verstecken - der
        // Schiedsrichter muss die Absage sehen, um sie im Zeitplan zurücknehmen zu können.
        assertEquals(
            LiveDashboardMatchState.SKIPPED,
            LiveDashboardLogic.deriveMatchState(null, null, start, null, listOf(false, false), skipped = true),
        )
    }

    @Test
    fun cancelledButActiveMatchStillShowsRunning() {
        // Wirklichkeit schlägt Plan: Der Zustand entsteht seit der Schutzregel in
        // EventScheduleService.setSlotSkipped nicht mehr neu, Altdaten können ihn aber tragen -
        // und dann darf das Dashboard nicht behaupten, es passiere gerade nichts.
        assertEquals(
            LiveDashboardMatchState.RUNNING,
            LiveDashboardLogic.deriveMatchState(activated, reallyStarted, start, null, listOf(false, false), skipped = true),
        )
    }

    @Test
    fun cancelledMatchWithResultsStaysFinished() {
        assertEquals(
            LiveDashboardMatchState.FINISHED,
            LiveDashboardLogic.deriveMatchState(null, null, start, start.plusMinutes(9), listOf(true, true), skipped = true),
        )
    }

    // --- selectForScope ---

    private fun match(state: LiveDashboardMatchState, name: String) = LiveDashboardMatchDto(
        matchId = UUID.randomUUID(),
        state = state,
        competitionId = UUID.randomUUID(),
        competitionName = "Coastal",
        competitionIdentifier = null,
        competitionShortName = null,
        categoryName = null,
        roundName = null,
        matchName = name,
        executionOrder = 0,
        startTime = start,
        startedAt = null,
        elapsedMinutes = null,
        teams = emptyList(),
        raceClockerPollError = null,
        raceClockerAutoPausedAt = null,
    )

    @Test
    fun liveScopeKeepsEveryRunningMatch() {
        val matches = listOf(
            match(LiveDashboardMatchState.FINISHED, "Vorlauf 1"),
            match(LiveDashboardMatchState.RUNNING, "Vorlauf 2"),
            match(LiveDashboardMatchState.RUNNING, "Vorlauf 3"),
            match(LiveDashboardMatchState.UPCOMING, "Finale"),
        )

        val selected = LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.LIVE)

        assertEquals(listOf("Vorlauf 2", "Vorlauf 3"), selected.map { it.matchName })
    }

    @Test
    fun liveScopeKeepsMatchesWaitingToBeFinished() {
        // Der Kern der D15-Korrektur: ohne diesen Zweig bliebe der Lauf, auf dessen Beenden alles
        // wartet, aus dem Live-Tab verschwunden.
        val matches = listOf(
            match(LiveDashboardMatchState.FINISHED, "Vorlauf 1"),
            match(LiveDashboardMatchState.AWAITING_FINISH, "Vorlauf 2"),
            match(LiveDashboardMatchState.UPCOMING, "Finale"),
        )

        val selected = LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.LIVE)

        assertEquals(listOf("Vorlauf 2"), selected.map { it.matchName })
    }

    @Test
    fun liveScopeKeepsRunningAndAwaitingSideBySide() {
        val matches = listOf(
            match(LiveDashboardMatchState.AWAITING_FINISH, "Vorlauf 1"),
            match(LiveDashboardMatchState.RUNNING, "Vorlauf 2"),
            match(LiveDashboardMatchState.UPCOMING, "Finale"),
        )

        val selected = LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.LIVE)

        assertEquals(listOf("Vorlauf 1", "Vorlauf 2"), selected.map { it.matchName })
    }

    @Test
    fun liveScopeFallsBackToTheNextUpcomingMatch() {
        val matches = listOf(
            match(LiveDashboardMatchState.FINISHED, "Vorlauf 1"),
            match(LiveDashboardMatchState.UPCOMING, "Vorlauf 2"),
            match(LiveDashboardMatchState.UPCOMING, "Finale"),
        )

        val selected = LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.LIVE)

        assertEquals(listOf("Vorlauf 2"), selected.map { it.matchName })
    }

    @Test
    fun liveScopeSkipsCancelledMatchesWhenPickingTheNextOne() {
        // Der Ausschnitt "was ist jetzt dran" darf nicht auf einem abgesagten Lauf stehen bleiben;
        // in der Gesamtliste (ALL) bleibt er als gekennzeichneter Eintrag sichtbar.
        val matches = listOf(
            match(LiveDashboardMatchState.FINISHED, "Vorlauf 1"),
            match(LiveDashboardMatchState.SKIPPED, "Vorlauf 2"),
            match(LiveDashboardMatchState.UPCOMING, "Finale"),
        )

        val selected = LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.LIVE)

        assertEquals(listOf("Finale"), selected.map { it.matchName })
        assertEquals(3, LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.ALL).size)
    }

    @Test
    fun liveScopeIsEmptyWhenNothingIsRunningOrUpcoming() {
        val matches = listOf(match(LiveDashboardMatchState.FINISHED, "Vorlauf 1"))

        assertTrue(LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.LIVE).isEmpty())
    }

    @Test
    fun allScopeKeepsEverything() {
        val matches = listOf(
            match(LiveDashboardMatchState.FINISHED, "Vorlauf 1"),
            match(LiveDashboardMatchState.RUNNING, "Vorlauf 2"),
            match(LiveDashboardMatchState.UNSCHEDULED, "Finale"),
        )

        assertEquals(3, LiveDashboardLogic.selectForScope(matches, LiveDashboardScope.ALL).size)
    }

    // --- requirementApplies ---

    @Test
    fun globalAssignmentAppliesToEveryone() {
        assertTrue(LiveDashboardLogic.requirementApplies(listOf(null), UUID.randomUUID()))
        assertTrue(LiveDashboardLogic.requirementApplies(listOf(null), null))
    }

    @Test
    fun namedAssignmentAppliesOnlyToMatchingRole() {
        val roleId = UUID.randomUUID()
        assertTrue(LiveDashboardLogic.requirementApplies(listOf(roleId), roleId))
        assertFalse(LiveDashboardLogic.requirementApplies(listOf(roleId), UUID.randomUUID()))
        assertFalse(LiveDashboardLogic.requirementApplies(listOf(roleId), null))
    }

    // --- Schweregrade ---

    private val competitionA: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val competitionB: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
    private val requirementA: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000c1")

    @Test
    fun defaultsReproduceTodaysBehaviour() {
        assertEquals(CheckSeverity.CRITICAL, LiveDashboardLogic.defaultSeverity(CheckType.INVOICE_OPEN, false))
        assertEquals(CheckSeverity.CRITICAL, LiveDashboardLogic.defaultSeverity(CheckType.NOT_ON_WATER, false))
        // Pflichtbedingung rot, optionale Bedingung ohne Wirkung - wie vor der Einstellmöglichkeit
        assertEquals(CheckSeverity.CRITICAL, LiveDashboardLogic.defaultSeverity(CheckType.REQUIREMENT, false))
        assertEquals(CheckSeverity.OK, LiveDashboardLogic.defaultSeverity(CheckType.REQUIREMENT, true))
        assertEquals(
            CheckSeverity.WARNING,
            LiveDashboardLogic.defaultSeverity(CheckType.REQUIREMENT_TIME_WINDOW, false)
        )
    }

    @Test
    fun fulfilledCheckIsAlwaysOk() {
        CheckSeverity.entries.forEach { configured ->
            assertEquals(EffectiveSeverity.OK, LiveDashboardLogic.effectiveSeverity(true, configured))
        }
    }

    @Test
    fun unfulfilledCheckFollowsConfiguration() {
        // Stufe OK heißt "zählt nicht", nicht "ist in Ordnung" - deshalb NEUTRAL, nicht OK.
        assertEquals(EffectiveSeverity.NEUTRAL, LiveDashboardLogic.effectiveSeverity(false, CheckSeverity.OK))
        assertEquals(EffectiveSeverity.WARNING, LiveDashboardLogic.effectiveSeverity(false, CheckSeverity.WARNING))
        assertEquals(EffectiveSeverity.CRITICAL, LiveDashboardLogic.effectiveSeverity(false, CheckSeverity.CRITICAL))
    }

    @Test
    fun worstSeverityTakesTheHighestRankAndNeutralWhenEmpty() {
        assertEquals(EffectiveSeverity.NEUTRAL, LiveDashboardLogic.worstSeverity(emptyList()))
        assertEquals(
            EffectiveSeverity.CRITICAL,
            LiveDashboardLogic.worstSeverity(
                listOf(EffectiveSeverity.OK, EffectiveSeverity.CRITICAL, EffectiveSeverity.WARNING)
            )
        )
        assertEquals(
            EffectiveSeverity.OK,
            LiveDashboardLogic.worstSeverity(listOf(EffectiveSeverity.NEUTRAL, EffectiveSeverity.OK))
        )
    }

    @Test
    fun requirementSeverityCombinesMissingAndTimeWindow() {
        // abgehakt, im Fenster
        assertEquals(
            EffectiveSeverity.OK,
            LiveDashboardLogic.requirementSeverity(
                true, TimeCheckStatus.OK, CheckSeverity.CRITICAL, CheckSeverity.WARNING
            )
        )
        // abgehakt, zu spät -> das Zeitfenster entscheidet
        assertEquals(
            EffectiveSeverity.WARNING,
            LiveDashboardLogic.requirementSeverity(
                true, TimeCheckStatus.LATE, CheckSeverity.CRITICAL, CheckSeverity.WARNING
            )
        )
        // nicht abgehakt -> das Zeitfenster ist bedeutungslos
        assertEquals(
            EffectiveSeverity.CRITICAL,
            LiveDashboardLogic.requirementSeverity(
                false, TimeCheckStatus.NOT_CHECKED, CheckSeverity.CRITICAL, CheckSeverity.WARNING
            )
        )
        // kein Zeitfenster konfiguriert
        assertEquals(
            EffectiveSeverity.NEUTRAL,
            LiveDashboardLogic.requirementSeverity(false, null, CheckSeverity.OK, CheckSeverity.WARNING)
        )
    }

    @Test
    fun invoiceSeverityDistinguishesNoInvoiceFromPaid() {
        // Ohne Rechnung gibt es nichts zu bewerten
        assertEquals(
            EffectiveSeverity.NEUTRAL,
            LiveDashboardLogic.invoiceSeverity(LiveDashboardInvoiceState.NONE, CheckSeverity.CRITICAL)
        )
        // Die alte Regel im Frontend lautete `invoiceState === 'OPEN' ? 'error' : 'neutral'` - eine
        // bezahlte Rechnung steuerte NIE etwas zur Ampel bei. Grün bedeutet "geprüft und in
        // Ordnung"; darüber sagt die Rechnung nichts aus, also NEUTRAL statt OK.
        assertEquals(
            EffectiveSeverity.NEUTRAL,
            LiveDashboardLogic.invoiceSeverity(LiveDashboardInvoiceState.PAID, CheckSeverity.CRITICAL)
        )
        assertEquals(
            EffectiveSeverity.CRITICAL,
            LiveDashboardLogic.invoiceSeverity(LiveDashboardInvoiceState.OPEN, CheckSeverity.CRITICAL)
        )
        // Der Gnaden-Fall: offene Rechnung wird heute nicht geahndet
        assertEquals(
            EffectiveSeverity.NEUTRAL,
            LiveDashboardLogic.invoiceSeverity(LiveDashboardInvoiceState.OPEN, CheckSeverity.OK)
        )
    }

    @Test
    fun onWaterIsOnlyJudgedWhenItApplies() {
        // Wettkampf ohne An-/Abmeldung oder Lauf nicht aktiv: keine Aussage
        assertEquals(
            EffectiveSeverity.NEUTRAL,
            LiveDashboardLogic.onWaterSeverity(evaluated = false, onWater = false, configured = CheckSeverity.CRITICAL)
        )
        assertEquals(
            EffectiveSeverity.CRITICAL,
            LiveDashboardLogic.onWaterSeverity(evaluated = true, onWater = false, configured = CheckSeverity.CRITICAL)
        )
        // Auf dem Wasser ist keine erfüllte Teilnahmebedingung, sondern der unauffällige
        // Regelfall - wie bei einer bezahlten Rechnung bleibt das NEUTRAL, nicht OK.
        assertEquals(
            EffectiveSeverity.NEUTRAL,
            LiveDashboardLogic.onWaterSeverity(evaluated = true, onWater = true, configured = CheckSeverity.CRITICAL)
        )
    }

    // --- onWaterApplies ---

    /**
     * Deckt namentlich die Gating-Bedingung aus `LiveDashboardService.buildTeamDto` ab
     * (`matchRunning && checkInOutRequired && !deregistered`), statt sie im Test ein zweites Mal
     * abzuschreiben. Ändert sich der Service, muss diese Funktion mitziehen - sonst würde der Test
     * unbemerkt an einer Kopie vorbeilaufen, während der Wasser-Term im echten Code abweicht.
     */
    @Test
    fun onWaterAppliesOnlyDuringAnActiveRunWithCheckInOutAndNotDeregistered() {
        assertTrue(
            LiveDashboardLogic.onWaterApplies(matchRunning = true, checkInOutRequired = true, deregistered = false)
        )
        // Beachsprint-Opt-out: kein Auschecken am Steg, also nie eine Aussage.
        assertFalse(
            LiveDashboardLogic.onWaterApplies(matchRunning = true, checkInOutRequired = false, deregistered = false)
        )
        // Vor dem Start am Steg ist "nicht draußen" kein Fehler.
        assertFalse(
            LiveDashboardLogic.onWaterApplies(matchRunning = false, checkInOutRequired = true, deregistered = false)
        )
        // Abgemeldet fährt nicht mehr - für das Wasser gibt es nichts mehr zu prüfen.
        assertFalse(
            LiveDashboardLogic.onWaterApplies(matchRunning = true, checkInOutRequired = true, deregistered = true)
        )
        assertFalse(
            LiveDashboardLogic.onWaterApplies(matchRunning = false, checkInOutRequired = false, deregistered = true)
        )
    }

    @Test
    fun teamSeverityIsTheWorstOfItsChecks() {
        assertEquals(
            EffectiveSeverity.CRITICAL,
            LiveDashboardLogic.teamSeverity(
                requirementSeverities = listOf(EffectiveSeverity.OK),
                invoice = EffectiveSeverity.CRITICAL,
                onWater = EffectiveSeverity.NEUTRAL,
            )
        )
        // Mannschaft ohne jede Prüfung bleibt grau
        assertEquals(
            EffectiveSeverity.NEUTRAL,
            LiveDashboardLogic.teamSeverity(emptyList(), EffectiveSeverity.NEUTRAL, EffectiveSeverity.NEUTRAL)
        )
    }

    // --- Paritätstest gegen die alte Frontend-Formel ---

    /**
     * Bildet mit einer eigenen, von [LiveDashboardLogic] unabhängigen Rechnung nach, was das
     * Frontend vor dem Umbau auf einstellbare Schweregrade auslieferte (`common.ts`, Stand vor
     * Commit cf614f1d):
     *
     * ```
     * worstSeverity([
     *     missingRequired > 0 ? 'error'   : 'neutral',
     *     timeIssues      > 0 ? 'warning' : 'neutral',
     *     fulfilled       > 0 ? 'ok'      : 'neutral',
     *     invoiceState === 'OPEN' ? 'error' : 'neutral',
     *     matchActive && !deregistered && !onWaterAt ? 'error' : 'neutral',
     * ])
     * ```
     *
     * Bewusst über die [EffectiveSeverity]-Ordinalzahlen statt über [LiveDashboardLogic.worstSeverity]
     * gerechnet: dieser Test soll die neue zusammengesetzte Bewertung gegen die alte Formel prüfen,
     * nicht gegen eine zweite Abschrift der neuen Implementierung.
     */
    private fun oldFormulaSeverity(
        missingRequired: Int,
        timeIssues: Int,
        fulfilled: Int,
        invoiceState: LiveDashboardInvoiceState,
        matchActive: Boolean,
        deregistered: Boolean,
        onWater: Boolean,
    ): EffectiveSeverity {
        val signals = listOf(
            if (missingRequired > 0) EffectiveSeverity.CRITICAL else EffectiveSeverity.NEUTRAL,
            if (timeIssues > 0) EffectiveSeverity.WARNING else EffectiveSeverity.NEUTRAL,
            if (fulfilled > 0) EffectiveSeverity.OK else EffectiveSeverity.NEUTRAL,
            if (invoiceState == LiveDashboardInvoiceState.OPEN) EffectiveSeverity.CRITICAL else EffectiveSeverity.NEUTRAL,
            if (matchActive && !deregistered && !onWater) EffectiveSeverity.CRITICAL else EffectiveSeverity.NEUTRAL,
        )
        return signals.reduce { acc, s -> if (s.ordinal > acc.ordinal) s else acc }
    }

    /**
     * Eine einzelne Teilnahmebedingung, wie sie am Steg abgehakt wird. [optional] ist eine eigene
     * Achse, weil die alte Formel unerfüllte optionale Bedingungen bewusst nicht mitzählte
     * (`!it.checked && !it.optional`) - ohne sie geprüft zu bekommen, würde der Paritätstest genau
     * den Pfad nie durchlaufen, an dem die Parität für Kann-Bedingungen hängt.
     */
    private data class TestRequirement(
        val checked: Boolean,
        val optional: Boolean = false,
        val timeCheckStatus: TimeCheckStatus?,
    )

    /**
     * [requirements] statt eines einzelnen `TestRequirement?`: die Paritätsbehauptung ruht gerade
     * auf mehr als einer Teilnahmebedingung je Mannschaft, weil die alte Formel über die ganze
     * Mannschaft zählte (`missingRequired`, `timeIssues`, `fulfilled`) und die neue je Bedingung
     * bewertet und danach das Schlechteste nimmt - ein einzelner Fall pro Kombination hätte diesen
     * Unterschied nie sichtbar gemacht.
     */
    private data class RequirementCase(val label: String, val requirements: List<TestRequirement>)

    private val requirementCases = listOf(
        RequirementCase("keine Bedingung", emptyList()),
        RequirementCase(
            "eine erfüllte Bedingung",
            listOf(TestRequirement(checked = true, timeCheckStatus = TimeCheckStatus.OK)),
        ),
        RequirementCase(
            "eine unerfüllte Pflichtbedingung",
            listOf(TestRequirement(checked = false, timeCheckStatus = null)),
        ),
        RequirementCase(
            "eine unerfüllte Pflichtbedingung, Zeitfenster noch nicht geprüft",
            listOf(TestRequirement(checked = false, timeCheckStatus = TimeCheckStatus.NOT_CHECKED)),
        ),
        RequirementCase(
            "eine unerfüllte optionale Bedingung",
            listOf(TestRequirement(checked = false, optional = true, timeCheckStatus = null)),
        ),
        RequirementCase(
            "eine mit verletztem Zeitfenster (zu spät)",
            listOf(TestRequirement(checked = true, timeCheckStatus = TimeCheckStatus.LATE)),
        ),
        RequirementCase(
            "eine mit verletztem Zeitfenster (zu früh)",
            listOf(TestRequirement(checked = true, timeCheckStatus = TimeCheckStatus.TOO_EARLY)),
        ),
        RequirementCase(
            "erfüllte und unerfüllte Pflichtbedingung zugleich",
            listOf(
                TestRequirement(checked = true, timeCheckStatus = TimeCheckStatus.OK),
                TestRequirement(checked = false, timeCheckStatus = null),
            ),
        ),
        RequirementCase(
            "erfüllte Pflicht- und unerfüllte optionale Bedingung zugleich",
            listOf(
                TestRequirement(checked = true, timeCheckStatus = TimeCheckStatus.OK),
                TestRequirement(checked = false, optional = true, timeCheckStatus = null),
            ),
        ),
    )

    /**
     * Prüft die Zusage des Schweregrad-Umbaus über den vollen Kombinationsraum statt an einem
     * einzelnen Beispiel: Ohne jede Konfiguration und mit `checkInOutRequired = true` muss die neue
     * zusammengesetzte Bewertung in JEDEM Fall dasselbe liefern wie die alte Frontend-Formel. Genau
     * ein Beispiel hat die frühere Regression bei der Rechnung (PAID -> OK statt NEUTRAL) und jetzt
     * dieselbe Fehlerklasse beim Wasser (onWater -> OK statt NEUTRAL) beide Male durchgelassen.
     *
     * Für `checkInOutRequired = false` (Beachsprint-Opt-out) weicht das neue Verhalten von der
     * alten Formel bewusst ab - dort gibt es kein Auschecken am Steg, "auf dem Wasser" darf also
     * nie mehr die Ampel verschlechtern. Das ist die gewollte Wirkung der Einstellung und deshalb
     * hier bewusst NICHT geprüft; die alte Formel kannte diesen Fall nie.
     */
    @Test
    fun compositeSeverityMatchesOldFormulaAcrossTheWholeCombinationSpace() {
        val checkInOutRequired = true
        val config = CheckSeverityConfig.empty

        for (requirementCase in requirementCases) {
            for (invoiceState in LiveDashboardInvoiceState.entries) {
                for (matchActive in listOf(false, true)) {
                    for (deregistered in listOf(false, true)) {
                        for (onWater in listOf(false, true)) {
                            val requirements = requirementCase.requirements

                            // Alte Formel: unabhängige Zähler, wie sie vor dem Umbau tatsächlich
                            // berechnet wurden (`LiveDashboardLogic.summarizeRequirements`, Stand vor
                            // Commit cf614f1d). Optionale Bedingungen zählten dort NIE als fehlend -
                            // ohne das `!it.optional` würde der optionale Zweig der neuen Formel
                            // (Standard-Schweregrad OK -> NEUTRAL statt CRITICAL) nie geprüft.
                            val missingRequired = requirements.count { !it.checked && !it.optional }
                            val timeIssues = requirements.count {
                                it.timeCheckStatus == TimeCheckStatus.LATE ||
                                    it.timeCheckStatus == TimeCheckStatus.TOO_EARLY
                            }
                            val fulfilled = requirements.count { it.checked }
                            val old = oldFormulaSeverity(
                                missingRequired, timeIssues, fulfilled, invoiceState, matchActive, deregistered, onWater,
                            )

                            // Neu: die tatsächliche, zusammengesetzte Bewertung aus der Implementierung.
                            // `optional` fließt in den Standard-Schweregrad ein (siehe
                            // `LiveDashboardLogic.defaultSeverity`) - ohne ihn an `severityFor` zu
                            // reichen, bekäme auch eine optionale Bedingung den CRITICAL-Standard.
                            val requirementSeverities = requirements.map {
                                LiveDashboardLogic.requirementSeverity(
                                    checked = it.checked,
                                    timeCheckStatus = it.timeCheckStatus,
                                    missingSeverity = config.severityFor(
                                        competitionA, CheckType.REQUIREMENT, optional = it.optional,
                                    ),
                                    timeWindowSeverity = config.severityFor(
                                        competitionA, CheckType.REQUIREMENT_TIME_WINDOW, optional = it.optional,
                                    ),
                                )
                            }
                            val invoice = LiveDashboardLogic.invoiceSeverity(
                                invoiceState,
                                config.severityFor(competitionA, CheckType.INVOICE_OPEN),
                            )
                            val onWaterEvaluated = LiveDashboardLogic.onWaterApplies(
                                matchRunning = matchActive,
                                checkInOutRequired = checkInOutRequired,
                                deregistered = deregistered,
                            )
                            val onWaterSeverity = LiveDashboardLogic.onWaterSeverity(
                                evaluated = onWaterEvaluated,
                                onWater = onWater,
                                configured = config.severityFor(competitionA, CheckType.NOT_ON_WATER),
                            )
                            val new = LiveDashboardLogic.teamSeverity(requirementSeverities, invoice, onWaterSeverity)

                            assertEquals(
                                old,
                                new,
                                "Bedingung=${requirementCase.label}, Rechnung=$invoiceState, " +
                                    "Lauf aktiv=$matchActive, abgemeldet=$deregistered, auf dem Wasser=$onWater",
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun unknownCheckTypesAreIgnoredInsteadOfCrashing() {
        // Eine Zeile aus einer neueren Version darf die Anzeige nicht lahmlegen.
        val config = LiveDashboardLogic.buildCheckSeverityConfig(
            listOf(
                Triple(competitionA, "INVOICE_OPEN" to null, "WARNING"),
                Triple(competitionA, "SOMETHING_NEW" to null, "CRITICAL"),
                Triple(competitionA, "REQUIREMENT" to requirementA, "NOT_A_SEVERITY"),
            )
        )

        assertEquals(1, config.overrides.size)
        assertEquals(CheckSeverity.WARNING, config.severityFor(competitionA, CheckType.INVOICE_OPEN))
    }

    @Test
    fun configuredValueBeatsDefaultAndStaysWithinItsCompetition() {
        val config = CheckSeverityConfig(
            mapOf(CheckSeverityKey(competitionA, CheckType.INVOICE_OPEN) to CheckSeverity.WARNING)
        )

        assertEquals(
            CheckSeverity.WARNING,
            config.severityFor(competitionA, CheckType.INVOICE_OPEN, optional = false)
        )
        // Ein anderer Wettkampf bleibt beim Standard
        assertEquals(
            CheckSeverity.CRITICAL,
            config.severityFor(competitionB, CheckType.INVOICE_OPEN, optional = false)
        )
        // Fehlender Eintrag -> Standard
        assertEquals(
            CheckSeverity.CRITICAL,
            config.severityFor(competitionA, CheckType.REQUIREMENT, requirementA, optional = false)
        )
    }

    // --- entriesToPersist ---

    private fun entry(
        competitionId: UUID = competitionA,
        checkType: CheckType = CheckType.INVOICE_OPEN,
        requirementId: UUID? = null,
        severity: CheckSeverity = CheckSeverity.WARNING,
    ) = CheckSeverityEntryDto(competitionId, checkType, requirementId, severity)

    @Test
    fun entryAtDefaultValueIsDropped() {
        // Der Standard braucht keine Zeile - die Tabelle bleibt dünn, siehe defaultSeverity.
        val result = LiveDashboardLogic.entriesToPersist(
            entries = listOf(entry(checkType = CheckType.INVOICE_OPEN, severity = CheckSeverity.CRITICAL)),
            competitionIds = setOf(competitionA),
            optionalByRequirement = emptyMap(),
            persistedRequirementIds = emptySet(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun entryOfForeignCompetitionIsDropped() {
        // Der Dialog schickt immer nur Wettkämpfe der eigenen Veranstaltung - alles andere ist ein
        // Fehler des Aufrufers und wird stillschweigend übergangen.
        val result = LiveDashboardLogic.entriesToPersist(
            entries = listOf(entry(competitionId = competitionB, severity = CheckSeverity.WARNING)),
            competitionIds = setOf(competitionA),
            optionalByRequirement = emptyMap(),
            persistedRequirementIds = emptySet(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun entryWithMadeUpRequirementIsDropped() {
        // Weder aktuell zugeordnet noch je gespeichert - genau das schützt vor einer erfundenen
        // Kennung, die am Fremdschlüssel auf participant_requirement scheitern würde.
        val result = LiveDashboardLogic.entriesToPersist(
            entries = listOf(
                entry(checkType = CheckType.REQUIREMENT, requirementId = requirementA, severity = CheckSeverity.WARNING)
            ),
            competitionIds = setOf(competitionA),
            optionalByRequirement = emptyMap(),
            persistedRequirementIds = emptySet(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun entryOfDeregisteredButAlreadyPersistedRequirementIsKept() {
        // Startpass wurde von der Veranstaltung abgemeldet, die gespeicherte Warnung soll die
        // Abmeldung trotzdem überleben: der Schreibweg ersetzt die gesamte Konfiguration der
        // Veranstaltung, ein hier verworfener Eintrag ist damit unwiderruflich gelöscht.
        val entryDto = entry(
            checkType = CheckType.REQUIREMENT,
            requirementId = requirementA,
            severity = CheckSeverity.WARNING,
        )
        val result = LiveDashboardLogic.entriesToPersist(
            entries = listOf(entryDto),
            competitionIds = setOf(competitionA),
            optionalByRequirement = emptyMap(),
            persistedRequirementIds = setOf(requirementA),
        )
        assertEquals(listOf(entryDto), result)
    }

    @Test
    fun entryWithoutRequirementIsAlwaysKept() {
        // Rechnung und "auf dem Wasser" hängen an keiner Teilnahmebedingung und damit an keinem
        // Fremdschlüssel, den eine abgemeldete Bedingung verletzen könnte.
        val entryDto = entry(checkType = CheckType.NOT_ON_WATER, severity = CheckSeverity.WARNING)
        val result = LiveDashboardLogic.entriesToPersist(
            entries = listOf(entryDto),
            competitionIds = setOf(competitionA),
            optionalByRequirement = emptyMap(),
            persistedRequirementIds = emptySet(),
        )
        assertEquals(listOf(entryDto), result)
    }

    @Test
    fun entryOfCurrentlyAssignedRequirementWithNonDefaultSeverityIsKept() {
        // Regulärer Fall: die Bedingung gehört zur Veranstaltung, der Wert weicht vom Standard ab.
        val entryDto = entry(
            checkType = CheckType.REQUIREMENT,
            requirementId = requirementA,
            severity = CheckSeverity.WARNING,
        )
        val result = LiveDashboardLogic.entriesToPersist(
            entries = listOf(entryDto),
            competitionIds = setOf(competitionA),
            optionalByRequirement = mapOf(requirementA to false),
            persistedRequirementIds = emptySet(),
        )
        assertEquals(listOf(entryDto), result)
    }

    @Test
    fun roleIsShortenedWithoutMakingTwoRolesLookAlike() {
        // Die echten Rollennamen der CRF beginnen beide mit "S" - Anfangsbuchstaben wären hier
        // wertlos, drei Buchstaben halten sie auseinander.
        assertEquals("Ste.", LiveDashboardLogic.roleAbbreviation("Steuerleute"))
        assertEquals("Sen.", LiveDashboardLogic.roleAbbreviation("Senior:in"))
    }

    @Test
    fun aShortRoleKeepsItsName() {
        // Ein Punkt hinter einem Wort, das nicht kürzer wird, wäre eine Lüge.
        assertEquals("Cox", LiveDashboardLogic.roleAbbreviation("Cox"))
        assertEquals("Bug", LiveDashboardLogic.roleAbbreviation(" Bug "))
    }

    @Test
    fun aMissingRoleStaysMissing() {
        assertEquals(null, LiveDashboardLogic.roleAbbreviation(null))
        assertEquals(null, LiveDashboardLogic.roleAbbreviation("   "))
    }
}
