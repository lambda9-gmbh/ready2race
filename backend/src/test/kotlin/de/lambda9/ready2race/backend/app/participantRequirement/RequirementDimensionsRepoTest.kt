package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementScopeLogic
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementScopeLogic.Fulfillment
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementScopeLogic.MatchScope
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementScopeLogic.Scope
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantHasRequirementForEventRepo
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantHasRequirementForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Was an den Dimensionen einer erfüllten Bedingung nur Postgres beantworten kann: der eindeutige
 * Index mit `nulls not distinct` und das Nachtragen der Bestandsdaten aus V202608141900. Die
 * Auswertungsregel selbst steht ohne Datenbank in [RequirementScopeLogicTest].
 */
class RequirementDimensionsRepoTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)
    private val samstag: LocalDate = LocalDate.of(2026, 8, 15)
    private val sonntag: LocalDate = LocalDate.of(2026, 8, 16)

    private class Seed(
        val eventId: UUID,
        val tag1: UUID,
        val tag2: UUID,
        val competitionId: UUID,
        val participantId: UUID,
        val requirementId: UUID,
    )

    /**
     * Eine zweitägige Veranstaltung mit einer Person und einer Bedingung. Die Tage werden
     * absichtlich in verkehrter Reihenfolge eingefügt - der "erste Wettkampftag" ist der mit dem
     * kleinsten Datum und nicht der zuerst angelegte.
     */
    private fun TestComprehensionScope<JEnv>.seed(): Seed {
        val eventId = UUID.randomUUID()
        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))

        val tag2 = UUID.randomUUID()
        !EVENT_DAY.insert(
            EventDayRecord(id = tag2, event = eventId, date = sonntag, createdAt = now, updatedAt = now)
        )
        val tag1 = UUID.randomUUID()
        !EVENT_DAY.insert(
            EventDayRecord(id = tag1, event = eventId, date = samstag, createdAt = now, updatedAt = now)
        )

        val competitionId = UUID.randomUUID()
        !COMPETITION.insert(
            CompetitionRecord(id = competitionId, event = eventId, createdAt = now, updatedAt = now)
        )

        val clubId = UUID.randomUUID()
        !CLUB.insert(ClubRecord(id = clubId, name = "Testverein", createdAt = now, updatedAt = now))

        val participantId = UUID.randomUUID()
        !PARTICIPANT.insert(
            ParticipantRecord(
                id = participantId,
                club = clubId,
                firstname = "Ilka",
                lastname = "Testerin",
                year = 1990,
                gender = Gender.F,
                createdAt = now,
                updatedAt = now,
            )
        )

        val requirementId = UUID.randomUUID()
        !PARTICIPANT_REQUIREMENT.insert(
            ParticipantRequirementRecord(
                id = requirementId,
                name = "Waage",
                optional = false,
                createdAt = now,
                updatedAt = now,
            )
        )

        return Seed(eventId, tag1, tag2, competitionId, participantId, requirementId)
    }

    private fun TestComprehensionScope<JEnv>.check(
        seed: Seed,
        eventDay: UUID? = null,
        competition: UUID? = null,
    ) = ParticipantHasRequirementForEventRepo.create(
        ParticipantHasRequirementForEventRecord(
            participant = seed.participantId,
            event = seed.eventId,
            participantRequirement = seed.requirementId,
            eventDay = eventDay,
            competition = competition,
            createdAt = now,
        )
    )

    /**
     * Der Kern des eindeutigen Index: zwei Zeilen mit verschiedenen Tagen sind verschieden, zwei
     * ohne Tag sind es nicht. Ohne `nulls not distinct` gälte in Postgres jedes null als eigener
     * Wert, und dieselbe veranstaltungsweite Erfüllung ließe sich beliebig oft eintragen - der
     * Schutz des früheren Primärschlüssels wäre ersatzlos verschwunden.
     */
    @Test
    fun nullsNotDistinctKeepsTheOldGuaranteeWhileAllowingDimensions() = testComprehension {
        val seed = seed()

        !check(seed, eventDay = seed.tag1)
        !check(seed, eventDay = seed.tag2)
        !check(seed, eventDay = seed.tag1, competition = seed.competitionId)
        !check(seed)

        assertEquals(4, (!ParticipantHasRequirementForEventRepo.getFulfillments(seed.eventId, seed.participantId)).size)

        // Zum Schluss, weil der fehlgeschlagene Einfügeversuch die laufende Anweisung abbricht:
        // dieselbe Zeile ohne Dimensionen ein zweites Mal. Die Verletzung der Eindeutigkeit
        // kommt als Fehler aus der Abfrage, nicht als Defekt - Jooq.query fängt sie ab.
        assertKIOFails { check(seed) }
    }

    /** Die Dimensionen kommen so zurück, wie sie geschrieben wurden - darauf baut die Auswertung. */
    @Test
    fun storedDimensionsDecideWhetherAMatchIsCovered() = testComprehension {
        val seed = seed()
        !check(seed, eventDay = seed.tag1)

        val fulfillments = (!ParticipantHasRequirementForEventRepo.getFulfillments(seed.eventId, seed.participantId))
            .map { Fulfillment(eventDay = it.eventDay, competition = it.competition) }
        assertEquals(listOf(Fulfillment(seed.tag1, null)), fulfillments)

        val scope = Scope(perEventDay = true, perCompetition = false)
        assertTrue(
            RequirementScopeLogic.isFulfilled(scope, fulfillments, MatchScope(seed.tag1, seed.competitionId))
        )
        assertFalse(
            RequirementScopeLogic.isFulfilled(scope, fulfillments, MatchScope(seed.tag2, seed.competitionId))
        )
        // Mit ausgeschaltetem Schalter zählt dieselbe Zeile überall - das Verhalten vor der
        // Migration, und der Grund, warum der Bestand bedenkenlos einen Tag eingetragen bekommt.
        assertTrue(
            RequirementScopeLogic.isFulfilled(
                Scope.forWholeEvent, fulfillments, MatchScope(seed.tag2, seed.competitionId)
            )
        )
    }

    /**
     * Das Nachtragen der Bestandsdaten. Die Anweisung ist wörtlich die aus V202608141900 - die
     * Migration selbst läuft in dieser leeren Testdatenbank über eine leere Tabelle und könnte
     * deshalb gar nichts beweisen.
     */
    @Test
    fun theBackfillPicksTheFirstCompetitionDayAndToleratesEventsWithoutOne() = testComprehension {
        val seed = seed()
        !check(seed)

        // Eine zweite Veranstaltung ganz ohne Wettkampftag: hier muss die Spalte null bleiben,
        // statt die Migration scheitern zu lassen.
        val ohneTagEventId = UUID.randomUUID()
        !EVENT.insert(EventRecord(id = ohneTagEventId, name = "Ohne Tage", createdAt = now, updatedAt = now))
        !ParticipantHasRequirementForEventRepo.create(
            ParticipantHasRequirementForEventRecord(
                participant = seed.participantId,
                event = ohneTagEventId,
                participantRequirement = seed.requirementId,
                createdAt = now,
            )
        )

        !Jooq.query {
            execute(
                """
                update ready2race.participant_has_requirement_for_event phrfe
                set event_day = (select ed.id
                                 from ready2race.event_day ed
                                 where ed.event = phrfe.event
                                 order by ed.date, ed.id
                                 limit 1)
                """.trimIndent()
            )
        }

        val nachgetragen = !PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.select { EVENT.eq(seed.eventId) }
        // Samstag, nicht der zuerst angelegte Sonntag.
        assertEquals(seed.tag1, nachgetragen.single().eventDay)
        // Und das Wettkampf-Feld bleibt leer: welcher Wettkampf gemeint war, weiß der Bestand nicht.
        assertNull(nachgetragen.single().competition)

        val ohneTag = !PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.select { EVENT.eq(ohneTagEventId) }
        assertNull(ohneTag.single().eventDay)
    }
}
