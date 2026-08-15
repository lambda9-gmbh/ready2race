package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.ParticipantRequirementService
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantHasRequirementForEventRepo
import de.lambda9.ready2race.backend.app.participantRequirement.entity.CheckedParticipantRequirement
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementApproveForParticipantDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementCheckForEventUpsertDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementError
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventHasParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantHasRequirementForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_HAS_PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Der Waage-Vorfall vom Regattatag: Die Scan-App rief den Ersetzen-Endpunkt
 * (`approveRequirementForEvent`) mit nur der gescannten Person auf, und dessen
 * `deleteWhereParticipantNotInList` löschte alle übrigen Bestätigungen derselben Bedingung.
 * Seither hat die App ihren eigenen, rein additiven Weg
 * ([ParticipantRequirementService.approveRequirementForParticipant]) - diese Tests belegen,
 * dass er niemand anderen anrührt, mit den Dimensionen aus V202608141900 (Tag, Wettkampf)
 * zusammenspielt und dass die Massen-Pflege im Verwaltungs-UI unverändert ersetzt.
 */
class ParticipantRequirementApproveForParticipantTest {

    private val now: LocalDateTime = LocalDateTime.now()
    private val heute: LocalDate = LocalDate.now()

    private class Seed(
        val eventId: UUID,
        val tagGestern: UUID,
        val tagHeute: UUID,
        val tagMorgen: UUID,
        val competitionA: UUID,
        val competitionB: UUID,
        val ilka: UUID,
        val bo: UUID,
        val kim: UUID,
        val requirementId: UUID,
    )

    /**
     * Eine dreitägige Veranstaltung (gestern/heute/morgen, damit "der Tag des Scans" eindeutig
     * bestimmbar ist und nicht der Ein-Tages-Rückfall greift), zwei Wettkämpfe, drei Personen
     * und eine Bedingung, deren Schalter je Test gesetzt werden.
     */
    private fun TestComprehensionScope<JEnv>.seed(
        perEventDay: Boolean = false,
        perCompetition: Boolean = false,
    ): Seed {
        val eventId = UUID.randomUUID()
        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))

        val tagGestern = UUID.randomUUID()
        val tagHeute = UUID.randomUUID()
        val tagMorgen = UUID.randomUUID()
        !EVENT_DAY.insert(
            EventDayRecord(id = tagGestern, event = eventId, date = heute.minusDays(1), createdAt = now, updatedAt = now)
        )
        !EVENT_DAY.insert(
            EventDayRecord(id = tagHeute, event = eventId, date = heute, createdAt = now, updatedAt = now)
        )
        !EVENT_DAY.insert(
            EventDayRecord(id = tagMorgen, event = eventId, date = heute.plusDays(1), createdAt = now, updatedAt = now)
        )

        val competitionA = UUID.randomUUID()
        !COMPETITION.insert(CompetitionRecord(id = competitionA, event = eventId, createdAt = now, updatedAt = now))
        val competitionB = UUID.randomUUID()
        !COMPETITION.insert(CompetitionRecord(id = competitionB, event = eventId, createdAt = now, updatedAt = now))

        val clubId = UUID.randomUUID()
        !CLUB.insert(ClubRecord(id = clubId, name = "Testverein", createdAt = now, updatedAt = now))

        val people = listOf("Ilka", "Bo", "Kim").map { firstname ->
            val id = UUID.randomUUID()
            !PARTICIPANT.insert(
                ParticipantRecord(
                    id = id,
                    club = clubId,
                    firstname = firstname,
                    lastname = "Testperson",
                    year = 1990,
                    gender = Gender.F,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            id
        }

        val requirementId = UUID.randomUUID()
        !PARTICIPANT_REQUIREMENT.insert(
            ParticipantRequirementRecord(
                id = requirementId,
                name = "Waage",
                optional = false,
                perEventDay = perEventDay,
                perCompetition = perCompetition,
                createdAt = now,
                updatedAt = now,
            )
        )
        !EVENT_HAS_PARTICIPANT_REQUIREMENT.insert(
            EventHasParticipantRequirementRecord(
                event = eventId,
                participantRequirement = requirementId,
                createdAt = now,
            )
        )

        return Seed(
            eventId, tagGestern, tagHeute, tagMorgen,
            competitionA, competitionB,
            people[0], people[1], people[2],
            requirementId,
        )
    }

    private fun TestComprehensionScope<JEnv>.scan(
        seed: Seed,
        participantId: UUID,
        approved: Boolean = true,
        note: String? = null,
        competitionId: UUID? = null,
    ) = !ParticipantRequirementService.approveRequirementForParticipant(
        seed.eventId,
        ParticipantRequirementApproveForParticipantDto(
            requirementId = seed.requirementId,
            participantId = participantId,
            approved = approved,
            note = note,
            competitionId = competitionId,
        ),
        SYSTEM_USER,
    )

    private fun TestComprehensionScope<JEnv>.fulfillments(seed: Seed, participantId: UUID) =
        !ParticipantHasRequirementForEventRepo.getFulfillments(seed.eventId, participantId)

    /**
     * Die Regression selbst: zwei Personen sind bestätigt, eine dritte wird gescannt - danach
     * müssen alle drei bestätigt sein. Vor der Trennung der Wege war genau das der
     * Datenverlust: der Scan lief über den Ersetzen-Endpunkt und löschte Ilka und Bo.
     */
    @Test
    fun aScanAddsWithoutDeletingOtherPeoplesApprovals() = testComprehension {
        val seed = seed()

        !ParticipantRequirementService.approveRequirementForEvent(
            seed.eventId,
            ParticipantRequirementCheckForEventUpsertDto(
                requirementId = seed.requirementId,
                approvedParticipants = listOf(
                    CheckedParticipantRequirement(id = seed.ilka, note = null),
                    CheckedParticipantRequirement(id = seed.bo, note = "unter Vorbehalt"),
                ),
            ),
            SYSTEM_USER,
        )

        scan(seed, seed.kim)

        assertEquals(1, fulfillments(seed, seed.ilka).size, "Ilka bleibt bestätigt")
        assertEquals(1, fulfillments(seed, seed.bo).size, "Bo bleibt bestätigt")
        val kim = fulfillments(seed, seed.kim).single()
        // Beide Schalter aus: die Erfüllung gilt veranstaltungsweit und trägt keine Dimensionen.
        assertNull(kim.eventDay)
        assertNull(kim.competition)
    }

    /** Doppel-Scan ist Alltag an der Waage: kein Fehler, eine Zeile, die Notiz überlebt. */
    @Test
    fun scanningTwiceIsIdempotentAndKeepsTheNote() = testComprehension {
        val seed = seed()

        scan(seed, seed.ilka, note = "58,3 kg")
        scan(seed, seed.ilka)

        val row = fulfillments(seed, seed.ilka).single()
        assertEquals("58,3 kg", row.note, "der zweite Scan ohne Notiz radiert die erste nicht aus")

        scan(seed, seed.ilka, note = "58,9 kg")
        assertEquals("58,9 kg", fulfillments(seed, seed.ilka).single().note)
    }

    /**
     * Bedingung je Wettkampf: die Bestätigung für Wettkampf A darf die bestehende für
     * Wettkampf B derselben Person nie entfernen - und der Widerruf für A lässt B stehen.
     */
    @Test
    fun perCompetitionApprovalsForDifferentCompetitionsCoexist() = testComprehension {
        val seed = seed(perCompetition = true)

        scan(seed, seed.ilka, competitionId = seed.competitionB)
        scan(seed, seed.ilka, competitionId = seed.competitionA)

        assertEquals(
            setOf(seed.competitionA, seed.competitionB),
            fulfillments(seed, seed.ilka).map { it.competition }.toSet(),
        )

        scan(seed, seed.ilka, approved = false, competitionId = seed.competitionA)

        assertEquals(
            seed.competitionB,
            fulfillments(seed, seed.ilka).single().competition,
            "der Widerruf für A lässt die Bestätigung für B stehen",
        )
    }

    /**
     * Bedingung je Tag: der Scan trägt den heutigen Wettkampftag ein, die gestrige Zeile
     * bleibt - sie war gestern gültig und bleibt es. Der Widerruf nimmt nur heute zurück.
     */
    @Test
    fun perEventDayScanRecordsTodayAndRevokeRemovesOnlyToday() = testComprehension {
        val seed = seed(perEventDay = true)

        // Die gestrige Waage, wie sie ein gestriger Scan hinterlassen hätte.
        !ParticipantHasRequirementForEventRepo.create(
            ParticipantHasRequirementForEventRecord(
                participant = seed.ilka,
                event = seed.eventId,
                participantRequirement = seed.requirementId,
                eventDay = seed.tagGestern,
                createdAt = now.minusDays(1),
            )
        )

        scan(seed, seed.ilka)

        assertEquals(
            setOf(seed.tagGestern, seed.tagHeute),
            fulfillments(seed, seed.ilka).map { it.eventDay }.toSet(),
            "der Scan trägt heute ein und lässt gestern stehen",
        )

        scan(seed, seed.ilka, approved = false)

        assertEquals(
            seed.tagGestern,
            fulfillments(seed, seed.ilka).single().eventDay,
            "der Widerruf nimmt nur den heutigen Tag zurück",
        )
    }

    /**
     * Veranstaltungsweite Bedingung: der Widerruf erwischt auch die tags-gestempelten Zeilen
     * aus der Bestandsmigration V202608141900 (Tag = erster Wettkampftag) - eine exakte
     * null/null-Löschung verfehlte sie. Andere Personen bleiben unberührt.
     */
    @Test
    fun wholeEventRevokeAlsoRemovesLegacyDayStampedRowsButOnlyForThisPerson() = testComprehension {
        val seed = seed()

        // So sehen die Zeilen aus, die die Bestandsmigration hinterlassen hat.
        !ParticipantHasRequirementForEventRepo.create(
            ParticipantHasRequirementForEventRecord(
                participant = seed.ilka,
                event = seed.eventId,
                participantRequirement = seed.requirementId,
                eventDay = seed.tagGestern,
                createdAt = now,
            )
        )
        scan(seed, seed.bo)

        scan(seed, seed.ilka, approved = false)

        assertTrue(fulfillments(seed, seed.ilka).isEmpty(), "auch die Altzeile mit Tag ist widerrufen")
        assertEquals(1, fulfillments(seed, seed.bo).size, "Bo bleibt bestätigt")
    }

    /**
     * Die Massen-Pflege im Verwaltungs-UI schickt weiterhin den Gesamtzustand und ersetzt ihn
     * auch: Wer nicht in der Liste steht, verliert seine Bestätigung. Diese Semantik ist für
     * die Transfer-Liste richtig und bleibt durch den neuen App-Weg unangetastet.
     */
    @Test
    fun bulkMaintenanceStillReplacesTheWholeState() = testComprehension {
        val seed = seed()

        val bulk = { approved: List<UUID> ->
            ParticipantRequirementService.approveRequirementForEvent(
                seed.eventId,
                ParticipantRequirementCheckForEventUpsertDto(
                    requirementId = seed.requirementId,
                    approvedParticipants = approved.map { CheckedParticipantRequirement(id = it, note = null) },
                ),
                SYSTEM_USER,
            )
        }

        !bulk(listOf(seed.ilka, seed.bo))
        !bulk(listOf(seed.bo, seed.kim))

        assertTrue(fulfillments(seed, seed.ilka).isEmpty(), "Ilka steht nicht mehr in der Liste")
        assertEquals(1, fulfillments(seed, seed.bo).size)
        assertEquals(1, fulfillments(seed, seed.kim).size)
    }

    /**
     * Der Abgleich im Verwaltungs-UI räumt nur innerhalb SEINES Wettkampfs auf.
     *
     * Ohne diese Grenze war der Weg aus dem Büro genauso gefährlich wie der Scan-Vorfall, der
     * diese Testklasse veranlasst hat: Wer an der Waage für Wettkampf A bestätigt war und im
     * Abgleich für Wettkampf B nicht angehakt ist, verlor seine Bestätigung - ohne dass es
     * jemand sah, weil beide Häkchen in der Verwaltung gleich aussehen.
     */
    @Test
    fun bulkMaintenanceOnlyClearsItsOwnCompetition() = testComprehension {
        val seed = seed(perCompetition = true)

        // Wettkampf A: Ilka ist bestätigt (der Stand, den die Waage erzeugt hat).
        !ParticipantRequirementService.approveRequirementForEvent(
            seed.eventId,
            ParticipantRequirementCheckForEventUpsertDto(
                requirementId = seed.requirementId,
                approvedParticipants = listOf(CheckedParticipantRequirement(id = seed.ilka, note = null)),
                competitionId = seed.competitionA,
            ),
            SYSTEM_USER,
        )

        // Wettkampf B: nur Bo - Ilka steht hier bewusst nicht in der Liste.
        !ParticipantRequirementService.approveRequirementForEvent(
            seed.eventId,
            ParticipantRequirementCheckForEventUpsertDto(
                requirementId = seed.requirementId,
                approvedParticipants = listOf(CheckedParticipantRequirement(id = seed.bo, note = null)),
                competitionId = seed.competitionB,
            ),
            SYSTEM_USER,
        )

        val ilka = fulfillments(seed, seed.ilka)
        assertEquals(1, ilka.size, "Ilkas Bestätigung für Wettkampf A muss stehen bleiben")
        assertEquals(seed.competitionA, ilka.single().competition)
        assertEquals(seed.competitionB, fulfillments(seed, seed.bo).single().competition)
    }

    /**
     * Und er schreibt in seinen Wettkampf, auch wenn die Person für einen anderen schon bestätigt
     * ist. Die frühere Trennung "kennt die Datenbank schon / noch nicht" fragte nur, ob
     * IRGENDEINE Zeile existiert, und zog dann bloß die Notiz nach - die Bestätigung für den
     * zweiten Wettkampf entstand nie, das Häkchen im Büro war folgenlos.
     */
    @Test
    fun bulkMaintenanceAddsTheMissingCompetitionRow() = testComprehension {
        val seed = seed(perCompetition = true)

        val bulk = { competitionId: UUID ->
            ParticipantRequirementService.approveRequirementForEvent(
                seed.eventId,
                ParticipantRequirementCheckForEventUpsertDto(
                    requirementId = seed.requirementId,
                    approvedParticipants = listOf(CheckedParticipantRequirement(id = seed.ilka, note = null)),
                    competitionId = competitionId,
                ),
                SYSTEM_USER,
            )
        }

        !bulk(seed.competitionA)
        !bulk(seed.competitionB)

        val competitions = fulfillments(seed, seed.ilka).mapNotNull { it.competition }.toSet()
        assertEquals(setOf(seed.competitionA, seed.competitionB), competitions)
    }

    /**
     * Ohne Wettkampf gibt es bei einer wettkampfbezogenen Bedingung keinen Abgleich: Der Aufruf
     * ersetzt den vollständigen Zustand, und ohne Rahmen wäre unklar, welchen. Lieber eine
     * Fehlermeldung als ein Löschen über alle Wettkämpfe hinweg.
     */
    @Test
    fun bulkMaintenanceRefusesWithoutACompetition() = testComprehension {
        val seed = seed(perCompetition = true)

        assertKIOFails(ParticipantRequirementError.CompetitionRequired) {
            ParticipantRequirementService.approveRequirementForEvent(
                seed.eventId,
                ParticipantRequirementCheckForEventUpsertDto(
                    requirementId = seed.requirementId,
                    approvedParticipants = listOf(CheckedParticipantRequirement(id = seed.ilka, note = null)),
                ),
                SYSTEM_USER,
            )
        }
    }
}
