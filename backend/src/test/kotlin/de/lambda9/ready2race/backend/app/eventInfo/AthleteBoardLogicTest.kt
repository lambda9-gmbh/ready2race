package de.lambda9.ready2race.backend.app.eventInfo

import com.fasterxml.jackson.databind.ObjectMapper
import de.lambda9.ready2race.backend.app.event.entity.PublicResultsVisibility
import de.lambda9.ready2race.backend.app.eventInfo.boundary.AthleteBoardLogic
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardStartState
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingCompetitionMatchInfo
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleLogic
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.FreeScheduleSlotInfo
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.PendingScheduleSlotInfo
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AthleteBoardLogicTest {

    private val mapper = ObjectMapper()
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 2, 10, 0)

    private fun filters(json: String) = mapper.readTree(json)

    // --- Konstanten ---

    @Test
    fun overdueGraceIsThirtyMinutes() {
        // Bewusst als Wert-Test: Diese Nachfrist wird von CompetitionMatchRepo.getUpcomingMatchesForBoard
        // verwendet und ist nicht über die Konfiguration einstellbar, daher schützt der Test vor
        // einer unbemerkten Änderung.
        assertEquals(30, AthleteBoardLogic.DEFAULT_OVERDUE_GRACE_MINUTES)
    }

    // --- resolveConfig ---

    @Test
    fun missingConfigurationYieldsDefaults() {
        val config = AthleteBoardLogic.resolveConfig(null, null)
        assertEquals(3, config.runningLimit)
        assertEquals(3, config.upcomingLimit)
        assertEquals(1, config.resultsLimit)
        assertTrue(config.showCountdown)
        assertEquals(15, config.refreshIntervalSeconds)
    }

    @Test
    fun fullConfigurationIsRead() {
        val config = AthleteBoardLogic.resolveConfig(
            filters("""{"running":5,"upcoming":4,"results":2,"showCountdown":false}"""),
            30,
        )
        assertEquals(5, config.runningLimit)
        assertEquals(4, config.upcomingLimit)
        assertEquals(2, config.resultsLimit)
        assertFalse(config.showCountdown)
        assertEquals(30, config.refreshIntervalSeconds)
    }

    @Test
    fun partialConfigurationKeepsDefaultsPerField() {
        val config = AthleteBoardLogic.resolveConfig(filters("""{"showCountdown":false}"""), null)
        assertEquals(3, config.runningLimit)
        assertEquals(3, config.upcomingLimit)
        assertEquals(1, config.resultsLimit)
        assertFalse(config.showCountdown)
        assertEquals(15, config.refreshIntervalSeconds)
    }

    @Test
    fun nonNumericLimitFallsBackToDefault() {
        val config = AthleteBoardLogic.resolveConfig(filters("""{"running":"viele"}"""), null)
        assertEquals(3, config.runningLimit)
    }

    @Test
    fun limitsAreClamped() {
        val config = AthleteBoardLogic.resolveConfig(
            filters("""{"running":500,"upcoming":0}"""),
            null,
        )
        assertEquals(20, config.runningLimit)
        assertEquals(1, config.upcomingLimit)
    }

    @Test
    fun nonPositiveDisplayDurationFallsBackToDefaultInterval() {
        val config = AthleteBoardLogic.resolveConfig(null, 0)
        assertEquals(15, config.refreshIntervalSeconds)
    }

    @Test
    fun refreshIntervalNeverDropsBelowMinimum() {
        // Der Kiosk-Regler erlaubt 5 Sekunden Rotationsdauer. Die Rotation darf so schnell
        // sein, der Abfragetakt der öffentlichen Anzeige nicht — er wird angehoben.
        val config = AthleteBoardLogic.resolveConfig(null, 5)
        assertEquals(AthleteBoardLogic.MIN_REFRESH_INTERVAL_SECONDS, config.refreshIntervalSeconds)
    }

    @Test
    fun refreshIntervalAboveMinimumIsKept() {
        val config = AthleteBoardLogic.resolveConfig(null, 20)
        assertEquals(20, config.refreshIntervalSeconds)
    }

    // --- isCacheFresh ---

    @Test
    fun cacheIsFreshWithinTtl() {
        assertTrue(AthleteBoardLogic.isCacheFresh(now, now))
        assertTrue(AthleteBoardLogic.isCacheFresh(now.minusSeconds(4), now))
    }

    @Test
    fun cacheIsStaleFromTtlOnwards() {
        assertFalse(
            AthleteBoardLogic.isCacheFresh(
                now.minusSeconds(AthleteBoardLogic.CACHE_TTL_SECONDS.toLong()),
                now,
            )
        )
        assertFalse(AthleteBoardLogic.isCacheFresh(now.minusMinutes(10), now))
    }

    @Test
    fun cacheBuiltInTheFutureCountsAsFresh() {
        // LocalDateTime.now() ist nicht monoton; ein Uhrsprung rückwärts soll den Cache
        // nicht dauerhaft ungültig machen, sondern schlicht als frisch gelten.
        assertTrue(AthleteBoardLogic.isCacheFresh(now.plusSeconds(30), now))
    }

    // --- startState ---

    @Test
    fun matchWithoutStartTimeIsUnscheduled() {
        assertEquals(
            AthleteBoardStartState.UNSCHEDULED,
            AthleteBoardLogic.startState(null, now, true),
        )
    }

    @Test
    fun futureStartWithCountdownEnabled() {
        assertEquals(
            AthleteBoardStartState.COUNTDOWN,
            AthleteBoardLogic.startState(now.plusMinutes(5), now, true),
        )
    }

    @Test
    fun futureStartWithCountdownDisabled() {
        assertEquals(
            AthleteBoardStartState.SCHEDULED,
            AthleteBoardLogic.startState(now.plusMinutes(5), now, false),
        )
    }

    @Test
    fun passedStartTimeIsOverdueInsteadOfNegativeCountdown() {
        assertEquals(
            AthleteBoardStartState.OVERDUE,
            AthleteBoardLogic.startState(now.minusMinutes(3), now, true),
        )
    }

    @Test
    fun passedStartTimeIsOverdueEvenWithoutCountdown() {
        assertEquals(
            AthleteBoardStartState.OVERDUE,
            AthleteBoardLogic.startState(now.minusMinutes(3), now, false),
        )
    }

    @Test
    fun startTimeExactlyNowIsOverdue() {
        assertEquals(
            AthleteBoardStartState.OVERDUE,
            AthleteBoardLogic.startState(now, now, true),
        )
    }

    // --- sortByStartTime ---

    @Test
    fun matchesWithoutStartTimeSortToTheEnd() {
        val input: List<Pair<String, LocalDateTime?>> = listOf(
            "ohne" to null,
            "spät" to now.plusMinutes(30),
            "früh" to now.plusMinutes(5),
        )
        val sorted = AthleteBoardLogic.sortByStartTime(input) { it.second }
        assertEquals(listOf("früh", "spät", "ohne"), sorted.map { it.first })
    }

    @Test
    fun sortingIsStableForEqualStartTimes() {
        val same = now.plusMinutes(10)
        val input: List<Pair<String, LocalDateTime?>> = listOf(
            "a" to same,
            "b" to same,
            "c" to null,
        )
        val sorted = AthleteBoardLogic.sortByStartTime(input) { it.second }
        assertEquals(listOf("a", "b", "c"), sorted.map { it.first })
    }

    // --- placeholdersFromPendingSlots ---
    //
    // Die Filterung auf WAITING (SKIPPED/FREE/LINKED/OBSOLETE liefern keinen Kandidaten) sitzt
    // seit dem Zusammenlegen mit dem Live-Dashboard in `EventScheduleLogic.pendingSlotOrNull` und
    // ist dort geprüft (siehe EventScheduleLogicTest). Hier bleibt nur die reine Mapping-Prüfung:
    // [slots] enthält per Vertrag ausschließlich WAITING-Slots.

    private fun pendingSlot(
        startTime: LocalDateTime = now.plusMinutes(30),
        competitionName: String = "Kanu",
        roundName: String? = "Vorlauf",
        matchName: String? = "Lauf 1",
    ) = PendingScheduleSlotInfo(
        slotId = UUID.randomUUID(),
        setupMatchId = UUID.randomUUID(),
        startTime = startTime,
        competitionId = UUID.randomUUID(),
        competitionName = competitionName,
        roundName = roundName,
        matchName = matchName,
    )

    @Test
    fun waitingSlotBecomesPendingPlaceholder() {
        val slot = pendingSlot()

        val placeholders = AthleteBoardLogic.placeholdersFromPendingSlots(listOf(slot))

        assertEquals(1, placeholders.size)
        val placeholder = placeholders.single()
        assertTrue(placeholder.pendingRound)
        assertEquals(slot.setupMatchId, placeholder.matchId)
        assertEquals(slot.competitionId, placeholder.competitionId)
        assertEquals(slot.competitionName, placeholder.competitionName)
        assertEquals(slot.roundName, placeholder.roundName)
        assertEquals(slot.matchName, placeholder.matchName)
        assertEquals(slot.startTime, placeholder.scheduledStartTime)
        assertTrue(placeholder.teams.isEmpty())
    }

    @Test
    fun multiplePendingSlotsAllBecomePlaceholdersInOrder() {
        val first = pendingSlot(matchName = "erster")
        val second = pendingSlot(matchName = "zweiter")

        val placeholders = AthleteBoardLogic.placeholdersFromPendingSlots(listOf(first, second))

        assertEquals(listOf("erster", "zweiter"), placeholders.map { it.matchName })
    }

    // --- placeholdersFromFreeSlots ---
    //
    // Die Filterung auf FREE (nicht übersprungen) sitzt gemeinsam mit dem Live-Dashboard in
    // `EventScheduleLogic.freeSlotOrNull` und ist dort geprüft (siehe EventScheduleLogicTest).
    // Hier bleibt nur die reine Mapping-Prüfung: [slots] enthält per Vertrag ausschließlich
    // FREE-Slots. Nur gebaut, wenn die Veranstaltung Pausen auf öffentlichen Anzeigen erlaubt.

    private fun freeSlot(
        startTime: LocalDateTime = now.plusMinutes(45),
        name: String? = "Mittagspause",
    ) = FreeScheduleSlotInfo(
        slotId = UUID.randomUUID(),
        startTime = startTime,
        name = name,
    )

    @Test
    fun freeSlotBecomesNamedPlaceholderWithoutCompetition() {
        val slot = freeSlot()

        val placeholders = AthleteBoardLogic.placeholdersFromFreeSlots(listOf(slot))

        assertEquals(1, placeholders.size)
        val placeholder = placeholders.single()
        assertEquals(slot.slotId, placeholder.matchId)
        assertEquals(slot.name, placeholder.name)
        assertEquals(slot.startTime, placeholder.scheduledStartTime)
        assertFalse(placeholder.pendingRound)
        assertEquals(null, placeholder.competitionId)
        assertTrue(placeholder.teams.isEmpty())
    }

    @Test
    fun multipleFreeSlotsAllBecomePlaceholdersInOrder() {
        val first = freeSlot(name = "Frühstückspause")
        val second = freeSlot(name = "Mittagspause")

        val placeholders = AthleteBoardLogic.placeholdersFromFreeSlots(listOf(first, second))

        assertEquals(listOf("Frühstückspause", "Mittagspause"), placeholders.map { it.name })
    }

    // --- Absagen an echten Läufen ---
    //
    // Zwei Hälften derselben Zeitstrahl-Zeile, die zusammen die Regel "ein abgesagter Lauf steht
    // nicht in 'nächste Läufe'" ergeben: solange die Runde nicht gesetzt ist, fängt
    // EventScheduleLogic.pendingSlotOrNull die Absage ab (kein Platzhalter); danach gibt es einen
    // echten Lauf, den nur EventScheduleLogic.skippedMatchIdOrNull noch herausnimmt. Vorher stand
    // ein abgesagtes Finale unverändert in der Spalte "Nächster Lauf" der Athleten-Anzeige.
    // EventInfoService.mergeWithPendingPlaceholders setzt genau diese beiden Funktionen auf
    // dieselbe Slot-Liste an - hier nachgestellt, ohne Datenbank.

    private data class SlotRow(
        val setupMatchId: UUID,
        val matchName: String,
        val skipped: Boolean,
        val roundMaterialized: Boolean,
        val matchExists: Boolean,
    )

    private fun upcomingMatch(setupMatchId: UUID, matchName: String) =
        UpcomingCompetitionMatchInfo(
            matchId = setupMatchId,
            matchNumber = null,
            competitionId = UUID.randomUUID(),
            competitionName = "Mixed Doppelzweier",
            categoryName = null,
            scheduledStartTime = now.plusMinutes(20),
            placeName = null,
            roundNumber = null,
            roundName = "Finale",
            matchName = matchName,
            executionOrder = 0,
            teams = emptyList(),
        )

    /** Wie in EventInfoService: echte Läufe minus die Läufe abgesagter Slots, plus Platzhalter. */
    private fun upcomingFrom(rows: List<SlotRow>): List<String?> {
        val real = rows.filter { it.matchExists }.map { upcomingMatch(it.setupMatchId, it.matchName) }
        val hidden = rows.mapNotNull {
            EventScheduleLogic.skippedMatchIdOrNull(
                setupMatchId = it.setupMatchId,
                skipped = it.skipped,
                roundMaterialized = it.roundMaterialized,
                matchExists = it.matchExists,
            )
        }.toSet()
        val placeholders = AthleteBoardLogic.placeholdersFromPendingSlots(
            rows.mapNotNull { row ->
                EventScheduleLogic.pendingSlotOrNull(
                    slotId = UUID.randomUUID(),
                    setupMatchId = row.setupMatchId,
                    startTime = now.plusMinutes(20),
                    competitionId = UUID.randomUUID(),
                    competitionName = "Mixed Doppelzweier",
                    roundName = "Finale",
                    matchName = row.matchName,
                    skipped = row.skipped,
                    roundMaterialized = row.roundMaterialized,
                    matchExists = row.matchExists,
                )
            }
        )
        return (real.filterNot { it.matchId in hidden } + placeholders).map { it.matchName }
    }

    @Test
    fun cancelledMatchWithMaterializedRoundLeavesUpcoming() {
        val rows = listOf(
            SlotRow(UUID.randomUUID(), "abgesagt", skipped = true, roundMaterialized = true, matchExists = true),
        )

        assertEquals(emptyList(), upcomingFrom(rows))
    }

    @Test
    fun matchWithoutCancellationStaysInUpcoming() {
        val rows = listOf(
            SlotRow(UUID.randomUUID(), "läuft noch", skipped = false, roundMaterialized = true, matchExists = true),
        )

        assertEquals(listOf("läuft noch"), upcomingFrom(rows))
    }

    @Test
    fun cancellationHidesOnlyTheCancelledEntryBeforeAndAfterTheRoundIsSet() {
        // Ein Slot je Zustand, wie er am Regattatag nebeneinander vorkommt.
        val rows = listOf(
            SlotRow(UUID.randomUUID(), "gesetzt, abgesagt", skipped = true, roundMaterialized = true, matchExists = true),
            SlotRow(UUID.randomUUID(), "gesetzt, fährt", skipped = false, roundMaterialized = true, matchExists = true),
            SlotRow(UUID.randomUUID(), "wartend, abgesagt", skipped = true, roundMaterialized = false, matchExists = false),
            SlotRow(UUID.randomUUID(), "wartend", skipped = false, roundMaterialized = false, matchExists = false),
        )

        assertEquals(listOf("gesetzt, fährt", "wartend"), upcomingFrom(rows))
    }

    // --- isStillUpcoming ---
    //
    // Dieselbe Nachfrist wie in CompetitionMatchRepo.getUpcomingMatchesForBoard, angewandt auf die
    // Platzhalter, die EventInfoService.mergeWithPendingPlaceholders dazumischt. Ohne sie blieb ein
    // Programmpunkt ("Obleute-Besprechung, 20:30") auch um 22:32 noch in "nächste Läufe" stehen und
    // verdrängte aus dem gedeckelten Block die Läufe, die wirklich anstanden.

    private val grace: Duration =
        Duration.ofMinutes(AthleteBoardLogic.DEFAULT_OVERDUE_GRACE_MINUTES.toLong())

    @Test
    fun placeholderWithinGraceStays() {
        val slot = pendingSlot(startTime = now.minusMinutes(29))

        assertTrue(AthleteBoardLogic.isStillUpcoming(slot.startTime, now, grace))
    }

    @Test
    fun placeholderBeyondGraceDropsOut() {
        val slot = pendingSlot(startTime = now.minusMinutes(82))

        assertFalse(AthleteBoardLogic.isStillUpcoming(slot.startTime, now, grace))
    }

    @Test
    fun programItemBeyondGraceDropsOutAsWell() {
        val slot = freeSlot(startTime = now.minusMinutes(150))

        assertFalse(AthleteBoardLogic.isStillUpcoming(slot.startTime, now, grace))
    }

    @Test
    fun programItemWithinGraceStays() {
        val slot = freeSlot(startTime = now.minusMinutes(10))

        assertTrue(AthleteBoardLogic.isStillUpcoming(slot.startTime, now, grace))
    }

    @Test
    fun startExactlyOnTheGraceBoundaryDropsOut() {
        // `START_TIME > jetzt - Nachfrist` in getUpcomingMatchesForBoard ist echt größer; genau auf
        // der Grenze ist die Frist abgelaufen. Eine Minute davor gilt noch.
        assertFalse(AthleteBoardLogic.isStillUpcoming(now.minus(grace), now, grace))
        assertTrue(AthleteBoardLogic.isStillUpcoming(now.minus(grace).plusMinutes(1), now, grace))
    }

    @Test
    fun justPassedStartStaysVisibleAsOverdue() {
        // Das Zusammenspiel mit startState: gerade verstrichen heißt sichtbar bleiben und
        // "erwartet" zeigen, statt einen negativen Countdown zu rechnen.
        val startTime = now.minusSeconds(30)
        assertTrue(AthleteBoardLogic.isStillUpcoming(startTime, now, grace))
        assertEquals(
            AthleteBoardStartState.OVERDUE,
            AthleteBoardLogic.startState(startTime, now, true),
        )
    }

    @Test
    fun futureStartIsAlwaysUpcoming() {
        assertTrue(AthleteBoardLogic.isStillUpcoming(now.plusMinutes(45), now, grace))
    }

    @Test
    fun entryWithoutStartTimeStaysUpcoming() {
        // Wie in der Abfrage (`START_TIME IS NULL OR ...`): ein Lauf ohne gepflegte Startzeit
        // kann nicht überfällig sein und bleibt sichtbar.
        assertTrue(AthleteBoardLogic.isStillUpcoming(null, now, grace))
    }

    @Test
    fun withoutGraceEveryPassedStartDropsOut() {
        // Die Kiosk-Ansicht fragt ohne Nachfrist ab (START_TIME > jetzt); ihre Platzhalter
        // verschwinden entsprechend mit der Startzeit.
        assertFalse(AthleteBoardLogic.isStillUpcoming(now, now, Duration.ZERO))
        assertFalse(AthleteBoardLogic.isStillUpcoming(now.minusSeconds(1), now, Duration.ZERO))
        assertTrue(AthleteBoardLogic.isStillUpcoming(now.plusSeconds(1), now, Duration.ZERO))
    }

    // --- isPublicResult: ab welchem Zustand ein Lauf öffentlich als Ergebnis erscheint ---

    private val finishedAt: LocalDateTime = now.minusMinutes(5)

    @Test
    fun finishedMatchIsPublicOnBothStages() {
        // Der Beenden-Klick ist die Erklärung "der Stand ist final" - er zählt in beiden Stufen.
        assertTrue(
            AthleteBoardLogic.isPublicResult(finishedAt, true, PublicResultsVisibility.FINISHED_ONLY)
        )
        assertTrue(
            AthleteBoardLogic.isPublicResult(finishedAt, false, PublicResultsVisibility.FINISHED_ONLY)
        )
        assertTrue(
            AthleteBoardLogic.isPublicResult(finishedAt, true, PublicResultsVisibility.RESULTS_COMPLETE)
        )
    }

    @Test
    fun completeButUnfinishedMatchStaysHiddenOnTheStrictStage() {
        // Die Voreinstellung: bis zum Beenden kann noch eine Zeitstrafe kommen.
        assertFalse(
            AthleteBoardLogic.isPublicResult(null, true, PublicResultsVisibility.FINISHED_ONLY)
        )
    }

    @Test
    fun completeButUnfinishedMatchIsPublicOnTheLenientStage() {
        // Das Verhalten vor der Einstellung - weiter wählbar, aber nicht mehr die Vorgabe.
        assertTrue(
            AthleteBoardLogic.isPublicResult(null, true, PublicResultsVisibility.RESULTS_COMPLETE)
        )
    }

    @Test
    fun incompleteAndUnfinishedMatchIsNeverPublic() {
        assertFalse(
            AthleteBoardLogic.isPublicResult(null, false, PublicResultsVisibility.FINISHED_ONLY)
        )
        assertFalse(
            AthleteBoardLogic.isPublicResult(null, false, PublicResultsVisibility.RESULTS_COMPLETE)
        )
    }
}
