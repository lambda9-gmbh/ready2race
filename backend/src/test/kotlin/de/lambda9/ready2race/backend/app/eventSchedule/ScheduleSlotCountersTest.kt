package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionExecution.seedTwoRoundCompetition
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionDeregistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventScheduleSlotRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_DEREGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_SCHEDULE_SLOT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Slot-Zähler des Zeitplans gegen ein echtes Postgres — die drei korrelierten Unterabfragen in
 * [EventScheduleRepo.getSlots], die keine reine Funktion abbildet.
 *
 * Der Anlass ist der Vorfall vom 14.08.2026 auf der Coastal-Regatta: Eine Mannschaft wurde von
 * einem Lauf abgemeldet, und der Lauf stand danach als „Teilweise gewertet 1/5" da, obwohl niemand
 * gefahren war. Die eine Zahl beantwortete zwei Fragen. Seither trennt die Abfrage sie:
 * `match_teams_scored` ist „erledigt" (Abmeldung zählt mit, daran hängt die Kette),
 * `match_teams_raced` ist „gefahren" (Abmeldung zählt nicht, daran hängt die Teilwertung).
 */
class ScheduleSlotCountersTest {

    private val seedTime = LocalDateTime.of(2026, 8, 14, 8, 0)

    /** Die Setup-Runde, zu der dieser Setup-Lauf gehört — die Abmeldung hängt an der Runde. */
    private fun TestComprehensionScope<JEnv>.roundOf(setupMatchId: UUID): UUID =
        (!COMPETITION_SETUP_MATCH.select { ID.eq(setupMatchId) })
            .single()
            .competitionSetupRound!!

    /** Die Meldungen eines Laufs in Startnummernreihenfolge. */
    private fun TestComprehensionScope<JEnv>.registrationsOf(setupMatchId: UUID): List<UUID> =
        (!COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(setupMatchId) })
            .sortedBy { it.startNumber }
            .map { it.competitionRegistration!! }

    private fun TestComprehensionScope<JEnv>.deregister(registrationId: UUID, roundId: UUID) {
        !COMPETITION_DEREGISTRATION.insert(
            CompetitionDeregistrationRecord(
                competitionRegistration = registrationId,
                competitionSetupRound = roundId,
                reason = "Krankheit",
                createdAt = seedTime,
                updatedAt = seedTime,
            )
        )
    }

    private fun TestComprehensionScope<JEnv>.placeTeam(
        setupMatchId: UUID,
        registrationId: UUID,
        newPlace: Int,
    ) {
        !COMPETITION_MATCH_TEAM.update(
            f = { place = newPlace },
            condition = {
                COMPETITION_MATCH.eq(setupMatchId).and(COMPETITION_REGISTRATION.eq(registrationId))
            },
        )
    }

    /** Der Slot, der auf diesen Setup-Lauf zeigt — ohne ihn taucht der Lauf im Zeitplan nicht auf. */
    private fun TestComprehensionScope<JEnv>.slotFor(eventId: UUID, setupMatchId: UUID) {
        !EVENT_SCHEDULE_SLOT.insert(
            EventScheduleSlotRecord(
                id = UUID.randomUUID(),
                event = eventId,
                startTime = seedTime.plusHours(2),
                competitionSetupMatch = setupMatchId,
                createdAt = seedTime,
                updatedAt = seedTime,
            )
        )
    }

    /** Die vier Zähler des einen Slots dieses Events: total, erledigt, gefahren, abgemeldet. */
    private fun TestComprehensionScope<JEnv>.counters(eventId: UUID): List<Int> {
        val row = (!EventScheduleRepo.getSlots(eventId)).single()
        return listOf(
            row.get("match_teams_total", Int::class.java)!!,
            row.get("match_teams_scored", Int::class.java)!!,
            row.get("match_teams_raced", Int::class.java)!!,
            row.get("match_teams_deregistered", Int::class.java)!!,
        )
    }

    /**
     * Der Vorfall selbst, im Kleinen: zwei Boote, eines abgemeldet, keines gefahren. Erledigt ist
     * 1 — dagegen ist nichts zu sagen, für dieses Boot kommt kein Ergebnis mehr. Gefahren muss 0
     * sein; genau diese Null hat gefehlt.
     */
    @Test
    fun aDeregisteredTeamCountsAsSettledButNotAsRaced() = testComprehension {
        val seed = seedTwoRoundCompetition()
        val setupMatchId = seed.firstRoundMatchIds.first()
        slotFor(seed.eventId, setupMatchId)

        deregister(registrationsOf(setupMatchId).first(), roundOf(setupMatchId))

        assertEquals(listOf(2, 1, 0, 1), counters(seed.eventId))
    }

    /** Ohne Abmeldung sagen „erledigt" und „gefahren" dasselbe — die Trennung ändert nichts. */
    @Test
    fun withoutADeregistrationBothCountersAgree() = testComprehension {
        val seed = seedTwoRoundCompetition()
        val setupMatchId = seed.firstRoundMatchIds.first()
        slotFor(seed.eventId, setupMatchId)

        placeTeam(setupMatchId, registrationsOf(setupMatchId).first(), newPlace = 1)

        assertEquals(listOf(2, 1, 1, 0), counters(seed.eventId))
    }

    /** Ein gefahrenes Boot neben einer Abmeldung: erledigt 2, gefahren 1 — die echte Teilwertung. */
    @Test
    fun aPlacedTeamNextToADeregistrationCountsAsRaced() = testComprehension {
        val seed = seedTwoRoundCompetition()
        val setupMatchId = seed.firstRoundMatchIds.first()
        slotFor(seed.eventId, setupMatchId)

        val registrations = registrationsOf(setupMatchId)
        deregister(registrations[0], roundOf(setupMatchId))
        placeTeam(setupMatchId, registrations[1], newPlace = 1)

        assertEquals(listOf(2, 2, 1, 1), counters(seed.eventId))
    }

    /**
     * Alle abgemeldet: erledigt ist voll, gefahren bleibt 0. Der volle Zähler ist der wichtige
     * Teil — an ihm hängt, dass die Kette über den Lauf hinweggeht statt an ihm stehenzubleiben.
     */
    @Test
    fun aFullyDeregisteredMatchIsFullySettled() = testComprehension {
        val seed = seedTwoRoundCompetition()
        val setupMatchId = seed.firstRoundMatchIds.first()
        slotFor(seed.eventId, setupMatchId)

        val roundId = roundOf(setupMatchId)
        registrationsOf(setupMatchId).forEach { deregister(it, roundId) }

        assertEquals(listOf(2, 2, 0, 2), counters(seed.eventId))
    }
}
