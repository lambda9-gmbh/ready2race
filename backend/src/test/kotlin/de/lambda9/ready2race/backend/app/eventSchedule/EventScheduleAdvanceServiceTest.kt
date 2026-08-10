package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleService
import de.lambda9.ready2race.backend.app.eventSchedule.entity.AdvanceScheduleRequest
import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleError
import de.lambda9.ready2race.backend.app.eventSchedule.entity.ShiftTargetProblem
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventScheduleSlotRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_SCHEDULE_SLOT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `EventScheduleService.advanceAfterSkippedSlot` gegen ein echtes Postgres.
 *
 * Was diese Ebene prüft und keine reine Funktion prüfen kann: dass aus dem abgesagten Slot in der
 * Datenbank tatsächlich der richtige Block gebildet wird - der entfallene Slot bleibt stehen, die
 * Slots hinter dem Bis-Slot bleiben unangetastet - und dass eine Vorschau nichts schreibt. Genau
 * das ist am Renntag die riskante Stelle: Ein Vorziehen, das zu weit reicht, verschiebt einen Plan,
 * den die Vereine schon ausgedruckt haben.
 *
 * Der Zeitplan besteht hier ausschließlich aus freien Slots (Programmpunkten). Für die Frage, wie
 * weit vorgezogen wird, spielt es keine Rolle, ob an einem Slot ein Lauf hängt; ohne Wettkampf-
 * Setup bleibt der Aufbau lesbar.
 */
class EventScheduleAdvanceServiceTest {

    private val day = LocalDateTime.of(2026, 8, 17, 0, 0).toLocalDate()
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 9, 12, 0)

    private fun at(hour: Int, minute: Int): LocalDateTime = LocalDateTime.of(day, LocalTime.of(hour, minute))

    private fun TestComprehensionScope<JEnv>.adminId(): UUID =
        assertNotNull(
            !Jooq.query { select(APP_USER.ID).from(APP_USER).limit(1).fetchOne(APP_USER.ID) },
            "Ohne angelegten Benutzer lässt sich der Zeitplan nicht ändern",
        )

    private fun TestComprehensionScope<JEnv>.event(): UUID {
        val id = UUID.randomUUID()
        !EVENT.insert(EventRecord(id = id, name = "Testregatta", createdAt = now, updatedAt = now))
        return id
    }

    private fun TestComprehensionScope<JEnv>.slot(
        eventId: UUID,
        name: String,
        startTime: LocalDateTime,
        durationMinutes: Int? = null,
        skipped: Boolean = false,
    ): UUID {
        val id = UUID.randomUUID()
        !EVENT_SCHEDULE_SLOT.insert(
            EventScheduleSlotRecord(
                id = id,
                event = eventId,
                startTime = startTime,
                name = name,
                durationMinutes = durationMinutes,
                skippedAt = if (skipped) now else null,
                createdAt = now,
                updatedAt = now,
            )
        )
        return id
    }

    /**
     * Die Startzeiten aller Slots des Events. Nach Zeit UND Name sortiert: bei Zeitgleichheit - und
     * die ist hier der Normalfall, der entfallene Slot und sein Nachrücker teilen sich die Minute -
     * hat der Zeitplan keine Reihenfolge, die eine Zusicherung verdient.
     */
    private fun TestComprehensionScope<JEnv>.startTimes(eventId: UUID): List<Pair<String, LocalDateTime>> =
        (!Jooq.query {
            select(EVENT_SCHEDULE_SLOT.NAME, EVENT_SCHEDULE_SLOT.START_TIME)
                .from(EVENT_SCHEDULE_SLOT)
                .where(EVENT_SCHEDULE_SLOT.EVENT.eq(eventId))
                .fetch { it.value1()!! to it.value2()!! }
        }).sortedWith(compareBy({ it.second }, { it.first }))

    /**
     * Der Normalfall: Ein Lauf mit gepflegter Dauer entfällt, der Zeitplan rückt bis zur
     * Mittagspause nach. Die Pause zieht mit - sie ist der gewählte Bis-Slot - und verlängert sich
     * dadurch um die frei gewordene Zeit, statt sie mitten im Renntag verfallen zu lassen.
     */
    @Test
    fun aCancelledSlotsDurationMovesTheBlockUpToAndIncludingTheLunchBreak() = testComprehension {
        val eventId = event()
        val cancelled = slot(eventId, "Lauf A", at(10, 0), durationMinutes = 20, skipped = true)
        slot(eventId, "Lauf B", at(10, 20))
        slot(eventId, "Lauf C", at(10, 40))
        val lunch = slot(eventId, "Mittagspause", at(12, 0))
        slot(eventId, "Lauf D", at(13, 0))

        val preview = !EventScheduleService.advanceAfterSkippedSlot(
            eventId, cancelled, AdvanceScheduleRequest(targetSlotId = lunch, dryRun = false), adminId(),
        )
        assertEquals(true, preview.dto.applied)

        assertEquals(
            listOf(
                "Lauf A" to at(10, 0),
                "Lauf B" to at(10, 0),
                "Lauf C" to at(10, 20),
                "Mittagspause" to at(11, 40),
                "Lauf D" to at(13, 0),
            ),
            startTimes(eventId),
        )
    }

    /** Ohne gepflegte Dauer sagt der Abstand zum nächsten Slot, wie viel Zeit frei wird. */
    @Test
    fun withoutADurationTheGapToTheFollowingSlotIsUsed() = testComprehension {
        val eventId = event()
        val cancelled = slot(eventId, "Lauf A", at(10, 0), skipped = true)
        val b = slot(eventId, "Lauf B", at(10, 15))
        slot(eventId, "Lauf C", at(10, 45))

        !EventScheduleService.advanceAfterSkippedSlot(
            eventId, cancelled, AdvanceScheduleRequest(targetSlotId = b, dryRun = false), adminId(),
        )

        assertEquals(
            listOf("Lauf A" to at(10, 0), "Lauf B" to at(10, 0), "Lauf C" to at(10, 45)),
            startTimes(eventId),
        )
    }

    /** Der letzte Slot des Renntags: Es gibt nichts, was nachrücken könnte - auch nicht mit Dauer. */
    @Test
    fun withoutAFollowingSlotOnTheSameDayThereIsNoDelta() = testComprehension {
        val eventId = event()
        slot(eventId, "Lauf A", at(10, 0))
        val cancelled = slot(eventId, "Lauf B", at(10, 20), durationMinutes = 20, skipped = true)
        val nextDay = slot(eventId, "Lauf C", at(10, 0).plusDays(1))

        assertKIOFails(EventScheduleError.AdvanceDeltaUndeterminable(cancelled)) {
            EventScheduleService.advanceAfterSkippedSlot(
                eventId, cancelled, AdvanceScheduleRequest(targetSlotId = nextDay, dryRun = true), adminId(),
            )
        }
    }

    /** Das Angebot hängt an der Absage - ohne sie gibt es keine frei gewordene Zeit. */
    @Test
    fun advancingBehindASlotThatIsNotCancelledIsRejected() = testComprehension {
        val eventId = event()
        val notCancelled = slot(eventId, "Lauf A", at(10, 0), durationMinutes = 20)
        val b = slot(eventId, "Lauf B", at(10, 20))

        assertKIOFails(EventScheduleError.SlotNotSkipped(notCancelled)) {
            EventScheduleService.advanceAfterSkippedSlot(
                eventId, notCancelled, AdvanceScheduleRequest(targetSlotId = b, dryRun = true), adminId(),
            )
        }
    }

    /** Ein Bis-Slot vor dem entfallenen Slot kann nur aus einem veralteten Zeitplan-Tab kommen. */
    @Test
    fun aTargetOutsideTheAdvanceableBlockIsRejected() = testComprehension {
        val eventId = event()
        val first = slot(eventId, "Lauf A", at(9, 0))
        val cancelled = slot(eventId, "Lauf B", at(10, 0), durationMinutes = 20, skipped = true)
        slot(eventId, "Lauf C", at(10, 20))

        assertKIOFails(EventScheduleError.ShiftTargetInvalid(ShiftTargetProblem.TARGET_NOT_AFTER_START)) {
            EventScheduleService.advanceAfterSkippedSlot(
                eventId, cancelled, AdvanceScheduleRequest(targetSlotId = first, dryRun = true), adminId(),
            )
        }
    }

    /**
     * Die Vorschau ist folgenlos. Wer sie sich ansieht und den Dialog dann abbricht, lässt den
     * Zeitplan genau so zurück, wie die Absage ihn hinterlassen hat.
     */
    @Test
    fun aPreviewChangesNothing() = testComprehension {
        val eventId = event()
        val cancelled = slot(eventId, "Lauf A", at(10, 0), durationMinutes = 20, skipped = true)
        val b = slot(eventId, "Lauf B", at(10, 20))
        slot(eventId, "Lauf C", at(10, 40))

        val before = startTimes(eventId)

        val preview = !EventScheduleService.advanceAfterSkippedSlot(
            eventId, cancelled, AdvanceScheduleRequest(targetSlotId = b, dryRun = true), adminId(),
        )

        assertEquals(false, preview.dto.applied)
        // Die Vorschau nennt trotzdem beide Slots des Blocks - auch den, der stehen bleibt.
        assertEquals(2, preview.dto.entries.size)
        assertEquals(at(10, 0), preview.dto.entries.first { it.slotId == b }.newStartTime)
        assertEquals(before, startTimes(eventId))
    }
}
