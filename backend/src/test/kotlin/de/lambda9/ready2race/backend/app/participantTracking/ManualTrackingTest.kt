package de.lambda9.ready2race.backend.app.participantTracking

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.participantTracking.boundary.ParticipantTrackingService
import de.lambda9.ready2race.backend.app.participantTracking.entity.ManualTrackingRequest
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingChangeType
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingError
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingSource
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Der Ausnahmeweg neben dem Scanner: nachtragen, was am Steg niemand gescannt hat, und
 * berichtigen, was falsch erfasst wurde.
 *
 * Geprüft wird hier vor allem, was den Weg von einem zweiten Schreibpfad unterscheidet - dass
 * jede Anlage und jede Korrektur eine Begründung samt Vorher-/Nachher-Stand hinterlässt, und dass
 * ein per QR erfasster Eintrag nach der Korrektur weiterhin als QR-Eintrag zu erkennen ist.
 */
class ManualTrackingTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 9, 0)

    private fun at(hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 8, 14, hour, minute)

    private fun TestComprehensionScope<JEnv>.seed(): Pair<UUID, UUID> {
        val clubId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        !CLUB.insert(
            ClubRecord(
                id = clubId,
                name = "Testverein $clubId",
                createdAt = now,
                updatedAt = now,
            )
        )

        !PARTICIPANT.insert(
            ParticipantRecord(
                id = participantId,
                club = clubId,
                firstname = "Test",
                lastname = "Ruderin",
                year = 1990,
                gender = Gender.F,
                createdAt = now,
                updatedAt = now,
            )
        )

        !EVENT.insert(
            EventRecord(
                id = eventId,
                name = "Testregatta",
                createdAt = now,
                updatedAt = now,
            )
        )

        return participantId to eventId
    }

    private fun request(
        scanType: ParticipantScanType,
        scannedAt: LocalDateTime,
        reason: String = "Boot ohne Scan abgelegt",
    ) = ManualTrackingRequest(scanType = scanType, scannedAt = scannedAt, reason = reason)

    /**
     * Der Anlassfall: das Boot hat abgelegt, ohne dass jemand das Bändchen gezogen hat. Beide
     * Einträge fehlen und werden von Hand nachgetragen.
     */
    @Test
    fun aMissingCheckInAndCheckOutCanBeAddedByHand() = testComprehension {
        val (participantId, eventId) = seed()

        !ParticipantTrackingService.createManualEntry(
            participantId, eventId, SYSTEM_USER, request(ParticipantScanType.ENTRY, at(9, 30))
        )
        !ParticipantTrackingService.createManualEntry(
            participantId, eventId, SYSTEM_USER, request(ParticipantScanType.EXIT, at(10, 15))
        )

        val history = (!ParticipantTrackingService.history(participantId, eventId)).dto

        assertEquals(2, history.entries.size)
        assertEquals(ParticipantScanType.ENTRY, history.entries[0].scanType)
        assertEquals(at(9, 30), history.entries[0].scannedAt)
        assertEquals(ParticipantScanType.EXIT, history.entries[1].scanType)

        // Beide tragen ihre Herkunft: das ist der Unterschied, den die Oberfläche zeigen muss.
        assertTrue(history.entries.all { it.source == ParticipantTrackingSource.MANUAL })
        assertEquals(2, history.changes.size)
        assertTrue(history.changes.all { it.changeType == ParticipantTrackingChangeType.CREATE })
        assertTrue(history.changes.all { it.createdBy != null })
    }

    /** Ein Zeitpunkt Stunden in der Vergangenheit ist der Normalfall, kein Sonderfall. */
    @Test
    fun theTimestampIsNotBoundToTheCurrentInstant() = testComprehension {
        val (participantId, eventId) = seed()

        !ParticipantTrackingService.createManualEntry(
            participantId, eventId, SYSTEM_USER,
            request(ParticipantScanType.ENTRY, LocalDateTime.of(2020, 5, 1, 6, 0)),
        )
        // Auch vorausschauend: der Steg braucht keine Zukunftssperre, und eine Regatta plant.
        !ParticipantTrackingService.createManualEntry(
            participantId, eventId, SYSTEM_USER,
            request(ParticipantScanType.EXIT, LocalDateTime.of(2030, 5, 1, 6, 0)),
        )

        val history = (!ParticipantTrackingService.history(participantId, eventId)).dto
        assertEquals(2, history.entries.size)
    }

    /**
     * Die Korrektur eines per QR erfassten Eintrags. Der Eintrag bleibt ein QR-Eintrag - dass er
     * vom Scanner kam, wird durch die Berichtigung nicht unwahr - und trägt zusätzlich seine Spur.
     */
    @Test
    fun aQrEntryCanBeCorrectedAndStaysMarkedAsQr() = testComprehension {
        val (participantId, eventId) = seed()

        // So, wie der Scanner am Steg ihn anlegt.
        !ParticipantTrackingService.participantCheckInOut(participantId, eventId, SYSTEM_USER, checkIn = true)

        val before = (!ParticipantTrackingService.history(participantId, eventId)).dto
        val scanned = before.entries.single()
        assertEquals(ParticipantTrackingSource.QR, scanned.source)
        assertEquals(0, scanned.editCount)
        val originalAt = scanned.scannedAt

        !ParticipantTrackingService.updateEntry(
            trackingId = scanned.id,
            participantId = participantId,
            eventId = eventId,
            userId = SYSTEM_USER,
            request = request(
                ParticipantScanType.ENTRY,
                at(8, 45),
                reason = "Scan erst am Steg nachgeholt, abgelegt war das Boot um 8:45",
            ),
        )

        val after = (!ParticipantTrackingService.history(participantId, eventId)).dto
        val corrected = after.entries.single()

        assertEquals(ParticipantTrackingSource.QR, corrected.source, "Herkunft bleibt QR")
        assertEquals(1, corrected.editCount, "der Eintrag ist als berichtigt erkennbar")
        assertEquals(at(8, 45), corrected.scannedAt)

        // Vorher und Nachher stehen beide in der Spur - das ist die eigentliche Anforderung.
        val change = after.changes.single()
        assertEquals(ParticipantTrackingChangeType.UPDATE, change.changeType)
        assertEquals(ParticipantScanType.ENTRY, change.previousScanType)
        assertEquals(originalAt, change.previousScannedAt)
        assertEquals(at(8, 45), change.newScannedAt)
        assertTrue(change.reason.startsWith("Scan erst am Steg"))
        assertEquals(SYSTEM_USER, change.createdBy?.id)
    }

    /** Auch die Richtung lässt sich berichtigen, nicht nur die Uhrzeit. */
    @Test
    fun theDirectionCanBeCorrectedToo() = testComprehension {
        val (participantId, eventId) = seed()

        !ParticipantTrackingService.createManualEntry(
            participantId, eventId, SYSTEM_USER, request(ParticipantScanType.ENTRY, at(9, 30))
        )
        val entry = (!ParticipantTrackingService.history(participantId, eventId)).dto.entries.single()

        // Zweiter Eintrag, der die Kette gültig hält, und dann wird der erste umgedreht - das
        // muss scheitern, weil danach zwei EXIT hintereinander stünden.
        !ParticipantTrackingService.createManualEntry(
            participantId, eventId, SYSTEM_USER, request(ParticipantScanType.EXIT, at(10, 0))
        )

        assertKIOFails(ParticipantTrackingError.SequenceConflict) {
            ParticipantTrackingService.updateEntry(
                entry.id, participantId, eventId, SYSTEM_USER,
                request(ParticipantScanType.EXIT, at(9, 30), reason = "falsche Richtung erfasst"),
            )
        }
    }

    /** Eine Abmeldung ohne zugehörige Anmeldung bleibt auch von Hand ausgeschlossen. */
    @Test
    fun aManualCheckOutWithoutACheckInIsRejected() = testComprehension {
        val (participantId, eventId) = seed()

        assertKIOFails(ParticipantTrackingError.SequenceConflict) {
            ParticipantTrackingService.createManualEntry(
                participantId, eventId, SYSTEM_USER, request(ParticipantScanType.EXIT, at(10, 0))
            )
        }
    }

    /** Zwei Einträge auf dieselbe Sekunde: die Reihenfolge wäre nicht bestimmt. */
    @Test
    fun twoEntriesOnTheSameInstantAreRejected() = testComprehension {
        val (participantId, eventId) = seed()

        !ParticipantTrackingService.createManualEntry(
            participantId, eventId, SYSTEM_USER, request(ParticipantScanType.ENTRY, at(9, 30))
        )

        assertKIOFails(ParticipantTrackingError.TimestampCollision) {
            ParticipantTrackingService.createManualEntry(
                participantId, eventId, SYSTEM_USER, request(ParticipantScanType.EXIT, at(9, 30))
            )
        }
    }

    /** Ein fremder Eintrag lässt sich nicht über die eigene Veranstaltung verbiegen. */
    @Test
    fun anEntryOfAnotherEventCannotBeCorrected() = testComprehension {
        val (participantId, eventId) = seed()
        val (_, otherEventId) = seed()

        !ParticipantTrackingService.createManualEntry(
            participantId, eventId, SYSTEM_USER, request(ParticipantScanType.ENTRY, at(9, 30))
        )
        val entry = (!ParticipantTrackingService.history(participantId, eventId)).dto.entries.single()

        assertKIOFails(ParticipantTrackingError.TrackingEntryNotFound) {
            ParticipantTrackingService.updateEntry(
                entry.id, participantId, otherEventId, SYSTEM_USER,
                request(ParticipantScanType.ENTRY, at(9, 0)),
            )
        }
    }

    /**
     * Die Pflichtbegründung greift, bevor der Service überhaupt gerufen wird - Ktor lehnt den
     * Rumpf über [ManualTrackingRequest.validate] ab. Deshalb wird hier der Validator geprüft und
     * nicht der Service: er bekommt eine leere Begründung nie zu sehen.
     */
    @Test
    fun anEmptyReasonIsRejectedByValidation() {
        val blank = ManualTrackingRequest(
            scanType = ParticipantScanType.ENTRY,
            scannedAt = at(9, 30),
            reason = "   ",
        )

        assertIs<ValidationResult.Invalid>(blank.validate())
        assertNull(
            (ManualTrackingRequest(
                scanType = ParticipantScanType.ENTRY,
                scannedAt = at(9, 30),
                reason = "Boot ohne Scan abgelegt",
            ).validate() as? ValidationResult.Invalid),
            "eine echte Begründung passiert die Prüfung",
        )
    }
}
