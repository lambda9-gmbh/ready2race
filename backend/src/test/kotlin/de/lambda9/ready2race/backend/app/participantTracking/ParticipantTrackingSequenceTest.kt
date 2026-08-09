package de.lambda9.ready2race.backend.app.participantTracking

import de.lambda9.ready2race.backend.app.participantTracking.boundary.ParticipantTrackingLogic
import de.lambda9.ready2race.backend.app.participantTracking.boundary.ParticipantTrackingLogic.SequenceViolation
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Die Reihenfolgeregel ohne Datenbank. Sie ist der Grund, warum der manuelle Nachtrag mehr ist als
 * ein zweiter Schreibpfad: solange Einträge nur hinten anwuchsen, genügte ein Blick auf den
 * jüngsten Scan. Ein Eintrag mitten in der Historie kann dagegen eine Zeile widersprüchlich
 * machen, die Stunden später liegt - und genau das sieht eine Randprüfung nicht.
 */
class ParticipantTrackingSequenceTest {

    private val day: LocalDateTime = LocalDateTime.of(2026, 8, 14, 0, 0)

    private fun at(hour: Int, minute: Int = 0) = day.withHour(hour).withMinute(minute)

    private fun entry(scanType: ParticipantScanType, at: LocalDateTime) =
        ParticipantTrackingLogic.Entry(UUID.randomUUID(), scanType, at)

    private fun entry(scanType: ParticipantScanType, hour: Int, minute: Int = 0) =
        entry(scanType, at(hour, minute))

    @Test
    fun anEmptyHistoryIsValid() {
        assertNull(ParticipantTrackingLogic.validateSequence(emptyList()))
    }

    @Test
    fun theRegularOrderPasses() {
        val violation = ParticipantTrackingLogic.validateSequence(
            listOf(
                entry(ParticipantScanType.ENTRY, 9),
                entry(ParticipantScanType.EXIT, 10),
                entry(ParticipantScanType.ENTRY, 13),
                entry(ParticipantScanType.EXIT, 14),
            )
        )

        assertNull(violation)
    }

    /** Die Eingabereihenfolge zählt nicht - nur die Uhrzeiten. */
    @Test
    fun theOrderIsTakenFromTheTimestampsNotFromTheList() {
        val violation = ParticipantTrackingLogic.validateSequence(
            listOf(
                entry(ParticipantScanType.EXIT, 10),
                entry(ParticipantScanType.ENTRY, 9),
            )
        )

        assertNull(violation)
    }

    /** Seit ENTRY "in der Arena" heißt, ist das eine Rückkehr aus einer Arena, in der niemand war. */
    @Test
    fun aHistoryThatStartsWithAReturnIsRejected() {
        val violation = ParticipantTrackingLogic.validateSequence(
            listOf(entry(ParticipantScanType.EXIT, 10))
        )

        val outOfOrder = assertIs<SequenceViolation.OutOfOrder>(violation)
        assertEquals(at(10), outOfOrder.at)
        assertEquals(ParticipantScanType.ENTRY, outOfOrder.expected)
    }

    @Test
    fun twoEntriesInARowAreRejected() {
        val violation = ParticipantTrackingLogic.validateSequence(
            listOf(
                entry(ParticipantScanType.ENTRY, 9),
                entry(ParticipantScanType.ENTRY, 11),
            )
        )

        val outOfOrder = assertIs<SequenceViolation.OutOfOrder>(violation)
        assertEquals(at(11), outOfOrder.at)
        assertEquals(ParticipantScanType.EXIT, outOfOrder.expected)
    }

    /**
     * Ein Nachtrag vorne dreht die Rollen aller späteren Einträge um: aus der Hinfahrt um 10 Uhr
     * wird die Stelle, an der eine Rückkehr stehen müsste. Nicht der neue Eintrag ist der
     * Verstoß, sondern der bereits vorhandene - die Meldung muss deshalb auf 10 Uhr zeigen und
     * nicht auf 9.
     */
    @Test
    fun anInsertionFlipsTheRoleOfEveryLaterEntry() {
        val existing = listOf(
            entry(ParticipantScanType.ENTRY, 10),
            entry(ParticipantScanType.EXIT, 13),
        )

        val violation =
            ParticipantTrackingLogic.validateSequence(existing + entry(ParticipantScanType.ENTRY, 9))

        val outOfOrder = assertIs<SequenceViolation.OutOfOrder>(violation)
        assertEquals(at(10), outOfOrder.at, "der bereits vorhandene Eintrag bricht, nicht der neue")
        assertEquals(ParticipantScanType.EXIT, outOfOrder.expected)
    }

    /** Eine Korrektur, die ihren Nachfolger überholt: der Check-out rutscht hinter den nächsten Check-in. */
    @Test
    fun aCorrectionThatOvertakesTheNextEntryIsRejected() {
        val violation = ParticipantTrackingLogic.validateSequence(
            listOf(
                entry(ParticipantScanType.ENTRY, 9),
                // war 10:00, wird auf 13:30 korrigiert - und liegt damit hinter dem ENTRY um 13:00
                entry(ParticipantScanType.EXIT, 13, 30),
                entry(ParticipantScanType.ENTRY, 13),
                entry(ParticipantScanType.EXIT, 14),
            )
        )

        val outOfOrder = assertIs<SequenceViolation.OutOfOrder>(violation)
        assertEquals(at(13), outOfOrder.at)
        assertEquals(ParticipantScanType.EXIT, outOfOrder.expected)
    }

    /**
     * Der eigentliche Gewinn der Ganzketten-Prüfung: sie setzt die lückenlose Abwechslung nicht
     * voraus, sondern stellt sie fest. Diese Historie ist schon widersprüchlich, bevor jemand sie
     * anfasst - so etwas entsteht durch per SQL eingespielte Testdaten oder Bestände aus der Zeit
     * vor der Regel. Eine Prüfung, die nur die Nachbarn des angefassten Eintrags ansieht, würde
     * einen Nachtrag um 15 Uhr anstandslos durchlassen und den Widerspruch um 11 Uhr
     * weiterschleppen.
     */
    @Test
    fun aContradictionAlreadyInTheHistoryIsCaught() {
        val violation = ParticipantTrackingLogic.validateSequence(
            listOf(
                entry(ParticipantScanType.ENTRY, 9),
                entry(ParticipantScanType.EXIT, 10),
                entry(ParticipantScanType.EXIT, 11),
                entry(ParticipantScanType.ENTRY, 15),
            )
        )

        val outOfOrder = assertIs<SequenceViolation.OutOfOrder>(violation)
        assertEquals(at(11), outOfOrder.at)
        assertEquals(ParticipantScanType.ENTRY, outOfOrder.expected)
    }

    /**
     * Gleicher Zeitpunkt, verschiedene Richtungen: die Sortierung dürfte hier würfeln, und mit ihr
     * die Frage, ob die Person am Ende auf dem Wasser ist.
     */
    @Test
    fun twoEntriesOnTheSameInstantAreRejected() {
        val violation = ParticipantTrackingLogic.validateSequence(
            listOf(
                entry(ParticipantScanType.ENTRY, 9),
                entry(ParticipantScanType.EXIT, 9),
            )
        )

        val collision = assertIs<SequenceViolation.Collision>(violation)
        assertEquals(at(9), collision.at)
    }

    /** Eine Sekunde Abstand genügt - die Spalte ist ein `timestamp`, kein Datum. */
    @Test
    fun oneSecondApartIsEnough() {
        val violation = ParticipantTrackingLogic.validateSequence(
            listOf(
                ParticipantTrackingLogic.Entry(
                    UUID.randomUUID(),
                    ParticipantScanType.ENTRY,
                    at(9).withSecond(10),
                ),
                ParticipantTrackingLogic.Entry(
                    UUID.randomUUID(),
                    ParticipantScanType.EXIT,
                    at(9).withSecond(11),
                ),
            )
        )

        assertNull(violation)
    }
}
