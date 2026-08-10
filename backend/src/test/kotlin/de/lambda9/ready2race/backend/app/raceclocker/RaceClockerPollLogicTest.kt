package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollLogic
import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollLogic.PollMode
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Die Entscheidungen des Abruf-Jobs, losgelöst von Datenbank und HTTP: wen er beobachtet, in
 * welchem Takt, wann der Takt fällig ist, wann ein Lauf als gestartet gilt und wann sich seit dem
 * letzten Abruf überhaupt etwas geändert hat.
 */
class RaceClockerPollLogicTest {

    private val now = LocalDateTime.of(2026, 8, 14, 10, 0)

    private fun row(
        id: UUID = UUID.randomUUID(),
        rank: Int? = 1,
        result: String? = null,
        start: LocalTime? = null,
        penaltySeconds: Int? = null,
        penaltyNote: String? = null,
    ) = RaceClockerFeedRow(
        name = "Testverein",
        rank = rank,
        bib = null,
        wave = "AF1 CM1x",
        ids = listOf(id),
        result = result,
        start = start,
        penaltySeconds = penaltySeconds,
        penaltyNote = penaltyNote,
    )

    // --- Fenster ---

    @Test
    fun anActivatedMatchIsWatchedRegardlessOfItsPlannedTime() {
        assertTrue(
            RaceClockerPollLogic.isWatched(
                activated = true,
                startTime = now.minusHours(6),
                now = now,
                watchBeforeMinutes = 15,
                watchAfterMinutes = 120,
            )
        )
    }

    @Test
    fun anActivatedMatchWithoutARealStartStaysWatched() {
        // Aktiviert schlägt das Zeitfenster: Der Lauf kann längst vor oder nach seinem Plan
        // stattfinden, und was tatsächlich passiert, schlägt den Plan. Genau dieser Fall trägt
        // seit dem 09.08.2026 den Ist-Start nach - ein von der Kette an den Start gerufener Lauf
        // bliebe sonst "in Vorbereitung", bis das erste Boot durchs Ziel ist.
        assertTrue(
            RaceClockerPollLogic.isWatched(
                activated = true,
                startTime = LocalDateTime.of(2026, 8, 14, 10, 0),
                now = LocalDateTime.of(2026, 8, 14, 14, 0),
                watchBeforeMinutes = 15,
                watchAfterMinutes = 120,
            )
        )
    }

    @Test
    fun anUpcomingMatchIsWatchedInsideTheWindow() {
        assertTrue(
            RaceClockerPollLogic.isWatched(false, now.plusMinutes(10), now, 15, 120)
        )
        assertTrue(
            RaceClockerPollLogic.isWatched(false, now.minusMinutes(90), now, 15, 120)
        )
    }

    @Test
    fun theWindowBoundsAreInclusive() {
        assertTrue(RaceClockerPollLogic.isWatched(false, now.plusMinutes(15), now, 15, 120))
        assertTrue(RaceClockerPollLogic.isWatched(false, now.minusMinutes(120), now, 15, 120))
    }

    @Test
    fun outsideTheWindowNothingIsWatched() {
        assertFalse(RaceClockerPollLogic.isWatched(false, now.plusMinutes(16), now, 15, 120))
        assertFalse(RaceClockerPollLogic.isWatched(false, now.minusMinutes(121), now, 15, 120))
    }

    @Test
    fun withoutAPlannedStartTimeAnInactiveMatchIsNotWatched() {
        assertFalse(RaceClockerPollLogic.isWatched(false, null, now, 15, 120))
    }

    // --- Takt ---

    @Test
    fun oneRunningMatchPutsTheWholeEventIntoTheFastMode() {
        assertEquals(PollMode.ACTIVE, RaceClockerPollLogic.modeFor(anyRunning = true))
        assertEquals(PollMode.UPCOMING, RaceClockerPollLogic.modeFor(anyRunning = false))
    }

    @Test
    fun theConfiguredIntervalNeverFallsBelowTheFloor() {
        assertEquals(5, RaceClockerPollLogic.intervalSeconds(5))
        assertEquals(RaceClockerPollLogic.MIN_INTERVAL_SECONDS, RaceClockerPollLogic.intervalSeconds(1))
        assertEquals(RaceClockerPollLogic.MIN_INTERVAL_SECONDS, RaceClockerPollLogic.intervalSeconds(0))
        assertEquals(RaceClockerPollLogic.MIN_INTERVAL_SECONDS, RaceClockerPollLogic.intervalSeconds(-30))
    }

    // --- Fälligkeit ---

    @Test
    fun anEventThatWasNeverPolledIsDueImmediately() {
        assertTrue(RaceClockerPollLogic.isDue(null, now, 5))
    }

    @Test
    fun theIntervalMustHavePassed() {
        assertFalse(RaceClockerPollLogic.isDue(now.minusSeconds(4), now, 5))
        assertTrue(RaceClockerPollLogic.isDue(now.minusSeconds(5), now, 5))
        assertTrue(RaceClockerPollLogic.isDue(now.minusSeconds(30), now, 5))
    }

    // --- Start-Erkennung ---

    @Test
    fun aRecordedStartTimeCountsAsStarted() {
        assertTrue(RaceClockerPollLogic.startDetected(listOf(row(start = LocalTime.of(10, 3)))))
    }

    @Test
    fun aResultCountsAsStartedEvenWithoutAStartTime() {
        assertTrue(RaceClockerPollLogic.startDetected(listOf(row(result = "3:21.4"))))
        assertTrue(RaceClockerPollLogic.startDetected(listOf(row(result = "DNF"))))
    }

    @Test
    fun waitingRowsAreNotAStart() {
        assertFalse(RaceClockerPollLogic.startDetected(listOf(row(result = "Not started"))))
        assertFalse(RaceClockerPollLogic.startDetected(listOf(row(result = "In race..."))))
        assertFalse(RaceClockerPollLogic.startDetected(emptyList()))
    }

    @Test
    fun aBoatOnTheWaterIsAStartWhenItsStartWasTimed() {
        assertTrue(
            RaceClockerPollLogic.startDetected(
                listOf(row(result = "In race...", start = LocalTime.of(10, 3)))
            )
        )
    }

    // --- der nachgetragene Ist-Start ---
    //
    // Der Fall, der den ganzen Umbau ausgelöst hat (Entwurf 09.08.2026, §2.2): Ein von der Kette an
    // den Start gerufener Lauf soll seinen Ist-Start bekommen, sobald RaceClocker eine Startzeit
    // meldet - und zwar lange bevor ein Boot durchs Ziel ist.

    @Test
    fun aTimedStartCountsEvenWhileEveryBoatIsStillRacing() {
        assertEquals(
            LocalDateTime.of(2026, 8, 14, 10, 3),
            RaceClockerPollLogic.measuredStartFor(
                rows = listOf(
                    row(result = "In race...", start = LocalTime.of(10, 3)),
                    row(result = "In race...", start = LocalTime.of(10, 3)),
                ),
                existingStartedAt = null,
                now = LocalDateTime.of(2026, 8, 14, 10, 5),
            )
        )
    }

    @Test
    fun anExistingStartStampIsNeverMoved() {
        assertEquals(
            null,
            RaceClockerPollLogic.measuredStartFor(
                rows = listOf(row(result = "In race...", start = LocalTime.of(10, 3))),
                existingStartedAt = LocalDateTime.of(2026, 8, 14, 10, 1),
                now = LocalDateTime.of(2026, 8, 14, 10, 5),
            )
        )
    }

    @Test
    fun withoutATimedStartInTheFeedNothingIsStamped() {
        assertEquals(
            null,
            RaceClockerPollLogic.measuredStartFor(
                rows = listOf(row(result = "3:21.4"), row(result = "Not started")),
                existingStartedAt = null,
                now = LocalDateTime.of(2026, 8, 14, 10, 5),
            )
        )
    }

    @Test
    fun theRaceDayIsTheOneNearestToNow() {
        // Der Feed liefert nur die Uhrzeit; den Tag bestimmt die Nähe zu `now`. Ein Lauf kurz vor
        // Mitternacht, abgerufen kurz danach, gehört auf den Vortag …
        assertEquals(
            LocalDateTime.of(2026, 8, 13, 23, 55),
            RaceClockerPollLogic.measuredStartFor(
                rows = listOf(row(start = LocalTime.of(23, 55))),
                existingStartedAt = null,
                now = LocalDateTime.of(2026, 8, 14, 0, 10),
            )
        )
        // … und der Normalfall auf heute.
        assertEquals(
            LocalDateTime.of(2026, 8, 14, 10, 3),
            RaceClockerPollLogic.measuredStartFor(
                rows = listOf(row(start = LocalTime.of(10, 3))),
                existingStartedAt = null,
                now = LocalDateTime.of(2026, 8, 14, 10, 5),
            )
        )
    }

    @Test
    fun aRaceRunOnADifferentDayThanPlannedIsStampedOnTheDayItRan() {
        // Der Fall vom 10.08.2026: Testlauf am Montagabend, geplant für Sonntag darauf. Mit dem
        // geplanten Renntag stünde der Ist-Start sechs Tage in der Zukunft — die Anzeige klemmt
        // negative Laufzeiten auf 0 und zeigte dauerhaft „Läuft · 0 min". Der Stempel gehört auf
        // den Tag, an dem der Start tatsächlich gemessen wurde.
        assertEquals(
            LocalDateTime.of(2026, 8, 10, 22, 32, 9),
            RaceClockerPollLogic.measuredStartFor(
                rows = listOf(row(result = "In race...", start = LocalTime.of(22, 32, 9))),
                existingStartedAt = null,
                now = LocalDateTime.of(2026, 8, 10, 22, 33),
            )
        )
    }

    // --- Fingerabdruck ---

    @Test
    fun unchangedRowsKeepTheirFingerprint() {
        val id = UUID.randomUUID()
        val a = listOf(row(id = id, result = "3:21.4", start = LocalTime.of(10, 3)))
        val b = listOf(row(id = id, result = "3:21.4", start = LocalTime.of(10, 3)))

        assertEquals(RaceClockerPollLogic.fingerprint(a), RaceClockerPollLogic.fingerprint(b))
    }

    @Test
    fun everyFieldThatIsWrittenChangesTheFingerprint() {
        val id = UUID.randomUUID()
        val base = listOf(row(id = id, result = "3:21.4", start = LocalTime.of(10, 3)))

        assertNotEquals(
            RaceClockerPollLogic.fingerprint(base),
            RaceClockerPollLogic.fingerprint(listOf(row(id = id, result = "3:22.0", start = LocalTime.of(10, 3)))),
        )
        assertNotEquals(
            RaceClockerPollLogic.fingerprint(base),
            RaceClockerPollLogic.fingerprint(listOf(row(id = id, rank = 2, result = "3:21.4", start = LocalTime.of(10, 3)))),
        )
        assertNotEquals(
            RaceClockerPollLogic.fingerprint(base),
            RaceClockerPollLogic.fingerprint(
                listOf(row(id = id, result = "3:21.4", start = LocalTime.of(10, 3), penaltySeconds = 10))
            ),
        )
        assertNotEquals(
            RaceClockerPollLogic.fingerprint(base),
            RaceClockerPollLogic.fingerprint(
                listOf(row(id = id, result = "3:21.4", start = LocalTime.of(10, 3), penaltyNote = "Boje"))
            ),
        )
    }

    @Test
    fun theOrderTheRowsArriveInDoesNotMatter() {
        val first = row(result = "3:21.4")
        val second = row(rank = 2, result = "3:25.0")

        assertEquals(
            RaceClockerPollLogic.fingerprint(listOf(first, second)),
            RaceClockerPollLogic.fingerprint(listOf(second, first)),
        )
    }
}
