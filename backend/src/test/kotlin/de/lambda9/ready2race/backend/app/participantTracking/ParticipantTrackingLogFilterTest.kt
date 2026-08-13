package de.lambda9.ready2race.backend.app.participantTracking

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participantTracking.boundary.ParticipantTrackingService
import de.lambda9.ready2race.backend.app.participantTracking.control.ParticipantTrackingRepo
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingSource
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die beiden Filter des Status-Protokolls: "nur letzter Status je Person" und der Status-Filter.
 *
 * Der Fall, der zählt, ist die Kombination - die Sicherheitsfrage "wer hat sich aufs Wasser
 * abgemeldet und ist noch nicht zurück?". Sie ist nur dann richtig beantwortet, wenn der
 * Status-Filter NACH der Reduktion auf das jüngste Ereignis greift: Wer nach seinem EXIT wieder
 * ein ENTRY hat, ist zurück und darf bei onlyLatest+EXIT nicht auftauchen, obwohl ein EXIT von
 * ihm im Protokoll steht.
 */
class ParticipantTrackingLogFilterTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 9, 0)

    private fun at(hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 8, 14, hour, minute)

    private fun TestComprehensionScope<JEnv>.seedEvent(): UUID {
        val eventId = UUID.randomUUID()
        !EVENT.insert(
            EventRecord(
                id = eventId,
                name = "Testregatta",
                createdAt = now,
                updatedAt = now,
            )
        )
        return eventId
    }

    private fun TestComprehensionScope<JEnv>.seedParticipant(lastname: String): UUID {
        val clubId = UUID.randomUUID()
        val participantId = UUID.randomUUID()

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
                lastname = lastname,
                year = 1990,
                gender = Gender.F,
                createdAt = now,
                updatedAt = now,
            )
        )

        return participantId
    }

    private fun TestComprehensionScope<JEnv>.scan(
        participantId: UUID,
        eventId: UUID,
        scanType: ParticipantScanType,
        scannedAt: LocalDateTime,
    ) {
        !ParticipantTrackingRepo.insert(
            ParticipantTrackingRecord(
                id = UUID.randomUUID(),
                participant = participantId,
                event = eventId,
                scanType = scanType.name,
                scannedBy = SYSTEM_USER,
                scannedAt = scannedAt,
                source = ParticipantTrackingSource.QR.name,
            )
        )
    }

    private fun TestComprehensionScope<JEnv>.page(
        eventId: UUID,
        onlyLatest: Boolean,
        scanType: ParticipantScanType?,
    ) = (!ParticipantTrackingService.page(
        eventId = eventId,
        params = PaginationParameters(null, null, null, null),
        user = AppUserWithPrivilegesRecord(id = SYSTEM_USER),
        scope = Privilege.Scope.GLOBAL,
        onlyLatest = onlyLatest,
        scanType = scanType,
    ))

    /**
     * Der Anlassfall: "aufs Wasser" (letztes Ereignis EXIT) erscheint, "wieder zurück" (nach dem
     * EXIT kam ein ENTRY) erscheint nicht - obwohl beide ein EXIT im Protokoll haben.
     */
    @Test
    fun onlyLatestPlusExitShowsWhoIsStillOutAndNotWhoCameBack() = testComprehension {
        val eventId = seedEvent()

        // Noch auf dem Wasser: ENTRY, dann EXIT - das jüngste Ereignis ist die Abmeldung.
        val onTheWater = seedParticipant("AufDemWasser")
        scan(onTheWater, eventId, ParticipantScanType.ENTRY, at(9, 0))
        scan(onTheWater, eventId, ParticipantScanType.EXIT, at(10, 0))

        // Wieder zurück: EXIT, dann ENTRY - das jüngste Ereignis ist die Rückmeldung. Würde der
        // Status-Filter VOR der Reduktion greifen, bliebe von dieser Person das EXIT von 10:30
        // übrig und sie stünde fälschlich als "auf dem Wasser" in der Liste.
        val cameBack = seedParticipant("WiederZurueck")
        scan(cameBack, eventId, ParticipantScanType.ENTRY, at(9, 30))
        scan(cameBack, eventId, ParticipantScanType.EXIT, at(10, 30))
        scan(cameBack, eventId, ParticipantScanType.ENTRY, at(11, 0))

        val result = page(eventId, onlyLatest = true, scanType = ParticipantScanType.EXIT)

        assertEquals(1, result.data.size)
        assertEquals(onTheWater, result.data.single().participantId)
        assertEquals(ParticipantScanType.EXIT, result.data.single().scanType)
        assertEquals(at(10, 0), result.data.single().scannedAt)
        // Auch der Gesamtzähler zählt auf dem reduzierten und gefilterten Ergebnis.
        assertEquals(1, result.pagination.total)
    }

    /** Ohne Status-Filter liefert onlyLatest genau eine Zeile je Person - jeweils die jüngste. */
    @Test
    fun onlyLatestReducesToOneRowPerParticipant() = testComprehension {
        val eventId = seedEvent()

        val out = seedParticipant("Draussen")
        scan(out, eventId, ParticipantScanType.ENTRY, at(9, 0))
        scan(out, eventId, ParticipantScanType.EXIT, at(10, 0))

        val back = seedParticipant("Zurueck")
        scan(back, eventId, ParticipantScanType.ENTRY, at(9, 30))
        scan(back, eventId, ParticipantScanType.EXIT, at(10, 30))
        scan(back, eventId, ParticipantScanType.ENTRY, at(11, 0))

        val result = page(eventId, onlyLatest = true, scanType = null)

        assertEquals(2, result.data.size)
        assertEquals(2, result.pagination.total)
        val byParticipant = result.data.associateBy { it.participantId }
        assertEquals(ParticipantScanType.EXIT, byParticipant[out]?.scanType)
        assertEquals(ParticipantScanType.ENTRY, byParticipant[back]?.scanType)
    }

    /** Ohne die Filter bleibt das Protokoll, was es war: alle Ereignisse. */
    @Test
    fun withoutFiltersAllEventsAreListed() = testComprehension {
        val eventId = seedEvent()

        val out = seedParticipant("Draussen")
        scan(out, eventId, ParticipantScanType.ENTRY, at(9, 0))
        scan(out, eventId, ParticipantScanType.EXIT, at(10, 0))

        val back = seedParticipant("Zurueck")
        scan(back, eventId, ParticipantScanType.ENTRY, at(9, 30))
        scan(back, eventId, ParticipantScanType.EXIT, at(10, 30))
        scan(back, eventId, ParticipantScanType.ENTRY, at(11, 0))

        val result = page(eventId, onlyLatest = false, scanType = null)

        assertEquals(5, result.data.size)
        assertEquals(5, result.pagination.total)
    }

    /** Der Status-Filter allein filtert das volle Protokoll, ohne je Person zu reduzieren. */
    @Test
    fun scanTypeAloneFiltersTheFullLog() = testComprehension {
        val eventId = seedEvent()

        val back = seedParticipant("Zurueck")
        scan(back, eventId, ParticipantScanType.ENTRY, at(9, 30))
        scan(back, eventId, ParticipantScanType.EXIT, at(10, 30))
        scan(back, eventId, ParticipantScanType.ENTRY, at(11, 0))

        val result = page(eventId, onlyLatest = false, scanType = ParticipantScanType.EXIT)

        assertEquals(1, result.data.size)
        assertTrue(result.data.all { it.scanType == ParticipantScanType.EXIT })
    }
}
