package de.lambda9.ready2race.backend.app.participantTracking

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.participantTracking.boundary.ParticipantTrackingService
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingError
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
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test

/**
 * Die Reihenfolge am Steg: erst anmelden, dann abmelden - und nichts dazwischen erfinden.
 *
 * Seit ENTRY "auf dem Wasser" bedeutet, ist das keine Formalie mehr: ein EXIT ohne vorheriges ENTRY
 * wäre eine Rückkehr von einem Wasser, auf dem die Person nie war. Die Prüfung verglich vorher
 * gegen EXIT und ließ deshalb genau diesen Fall durch - eine nie gescannte Person hat
 * `currentStatus == null` und fiel durch beide Zweige hindurch.
 */
class CheckInOutOrderTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 9, 0)

    /** Verein, Person, Veranstaltung - mehr berührt der Check-in nicht. */
    private fun TestComprehensionScope<JEnv>.seed(): Pair<UUID, UUID> {
        val clubId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        !CLUB.insert(
            ClubRecord(
                id = clubId,
                // club.name ist unique und die Datenbank steht zwischen den Tests dieser Klasse
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

    /**
     * Der Fall, der vorher durchlief: Diese Person hat nie einen Scan, kann also nicht vom Wasser
     * zurückkommen.
     */
    @Test
    fun checkingOutWithoutAnyScanIsRejected() = testComprehension {
        val (participantId, eventId) = seed()

        assertKIOFails(ParticipantTrackingError.TeamNotCheckedIn) {
            ParticipantTrackingService.participantCheckInOut(participantId, eventId, SYSTEM_USER, checkIn = false)
        }
    }

    /** Der reguläre Ablauf bleibt unberührt - und nach der Rückkehr ist wieder Schluss. */
    @Test
    fun theRegularOrderStillWorksAndEndsAfterTheReturn() = testComprehension {
        val (participantId, eventId) = seed()

        assertKIOSucceeds {
            ParticipantTrackingService.participantCheckInOut(participantId, eventId, SYSTEM_USER, checkIn = true)
        }
        assertKIOSucceeds {
            ParticipantTrackingService.participantCheckInOut(participantId, eventId, SYSTEM_USER, checkIn = false)
        }
        // Zurück am Steg: ein zweites Abmelden hat keinen Bezug mehr.
        assertKIOFails(ParticipantTrackingError.TeamNotCheckedIn) {
            ParticipantTrackingService.participantCheckInOut(participantId, eventId, SYSTEM_USER, checkIn = false)
        }
    }

    /** Die Gegenrichtung war schon immer abgedeckt und muss es bleiben. */
    @Test
    fun checkingInTwiceIsRejected() = testComprehension {
        val (participantId, eventId) = seed()

        assertKIOSucceeds {
            ParticipantTrackingService.participantCheckInOut(participantId, eventId, SYSTEM_USER, checkIn = true)
        }
        assertKIOFails(ParticipantTrackingError.TeamAlreadyCheckedIn) {
            ParticipantTrackingService.participantCheckInOut(participantId, eventId, SYSTEM_USER, checkIn = true)
        }
    }
}
