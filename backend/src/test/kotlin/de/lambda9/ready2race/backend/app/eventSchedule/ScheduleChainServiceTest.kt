package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionExecution.SeededRoundProgression
import de.lambda9.ready2race.backend.app.competitionExecution.seedTwoRoundCompetition
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.app.event.entity.ChainProgressionMode
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleService
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventScheduleSlotRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Die Lauf-Kette gegen ein echtes Postgres - der Ablauf, den keine reine Funktion abbildet: Ein
 * einziges "Beenden" stößt die Kette zweimal an, weil die Folgerunden-Automatik am Ende von
 * `createNewRound` selbst noch einmal zieht. Genau daran ist am 10.08.2026 auf der
 * Coastal-Regatta eine Startgruppe zu viel an den Start gerufen worden: Der zweite Durchlauf fand
 * die eben gerufene Gruppe "nicht mehr aktivierbar" und lief zur übernächsten weiter.
 *
 * Der Aufbau ahmt den Vorfall nach: Der beendete Lauf ist der letzte seiner Runde (also entsteht
 * die Folgerunde), und hinter ihm stehen zwei fahrbereite Läufe eines anderen Wettkampfs - erst
 * mit dem zweiten lässt sich "eine Gruppe zu weit" überhaupt beobachten.
 */
class ScheduleChainServiceTest {

    private val day1 = LocalDateTime.of(2026, 8, 14, 0, 0).toLocalDate()
    private val day2 = LocalDateTime.of(2026, 8, 15, 0, 0).toLocalDate()
    private val seedTime = LocalDateTime.of(2026, 8, 14, 8, 0)

    private fun on(day: java.time.LocalDate, hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(day, java.time.LocalTime.of(hour, minute))

    // --- Vorrichtung ---------------------------------------------------------------------------

    /** Ein Slot des Zeitplans; ohne [setupMatch] ein Programmpunkt. */
    private fun TestComprehensionScope<JEnv>.slot(
        eventId: UUID,
        startTime: LocalDateTime,
        setupMatch: UUID? = null,
        name: String? = null,
    ): UUID {
        val id = UUID.randomUUID()
        !EVENT_SCHEDULE_SLOT.insert(
            EventScheduleSlotRecord(
                id = id,
                event = eventId,
                startTime = startTime,
                competitionSetupMatch = setupMatch,
                name = name,
                createdAt = seedTime,
                updatedAt = seedTime,
            )
        )
        return id
    }

    /**
     * Ein zweiter Wettkampf mit einer einzigen Runde und [count] bereits gesetzten, fahrbereiten
     * Läufen - die Rennen, die im Zeitplan hinter dem beendeten Lauf stehen. Bewusst ein eigener
     * Wettkampf: Läufe derselben Runde wie der beendete würden dessen Runde unvollständig machen
     * und die Folgerunden-Automatik gar nicht erst auslösen.
     */
    private fun TestComprehensionScope<JEnv>.seedSideRaces(eventId: UUID, count: Int): List<UUID> {
        val competitionId = UUID.randomUUID()
        val propertiesId = UUID.randomUUID()
        val roundId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        val eventRegistrationId = UUID.randomUUID()

        !COMPETITION.insert(
            CompetitionRecord(
                id = competitionId,
                event = eventId,
                createdAt = seedTime,
                updatedAt = seedTime,
            )
        )
        !COMPETITION_PROPERTIES.insert(
            CompetitionPropertiesRecord(
                id = propertiesId,
                competition = competitionId,
                identifier = "2",
                name = "Einer",
            )
        )
        !COMPETITION_SETUP.insert(
            CompetitionSetupRecord(
                competitionProperties = propertiesId,
                createdAt = seedTime,
                updatedAt = seedTime,
            )
        )
        !COMPETITION_SETUP_ROUND.insert(
            CompetitionSetupRoundRecord(
                id = roundId,
                competitionSetup = propertiesId,
                name = "Zeitfahren",
                required = true,
                useDefaultSeeding = true,
                placesOption = CompetitionSetupPlacesOption.EQUAL.name,
                nextRound = null,
            )
        )
        !CLUB.insert(
            ClubRecord(
                id = clubId,
                name = "Nebenverein $clubId",
                createdAt = seedTime,
                updatedAt = seedTime,
            )
        )
        !EVENT_REGISTRATION.insert(
            EventRegistrationRecord(
                id = eventRegistrationId,
                event = eventId,
                club = clubId,
                createdAt = seedTime,
                updatedAt = seedTime,
            )
        )

        return (1..count).map { number ->
            val setupMatchId = UUID.randomUUID()
            val registrationId = UUID.randomUUID()
            !COMPETITION_SETUP_MATCH.insert(
                CompetitionSetupMatchRecord(
                    id = setupMatchId,
                    competitionSetupRound = roundId,
                    weighting = number,
                    name = "Seitenrennen $number",
                    executionOrder = number,
                    teams = 1,
                )
            )
            !COMPETITION_REGISTRATION.insert(
                CompetitionRegistrationRecord(
                    id = registrationId,
                    eventRegistration = eventRegistrationId,
                    competition = competitionId,
                    club = clubId,
                    name = "Nebenboot $number",
                    createdAt = seedTime,
                    updatedAt = seedTime,
                    teamNumber = number,
                )
            )
            !COMPETITION_MATCH.insert(
                CompetitionMatchRecord(
                    competitionSetupMatch = setupMatchId,
                    createdAt = seedTime,
                    updatedAt = seedTime,
                )
            )
            !COMPETITION_MATCH_TEAM.insert(
                CompetitionMatchTeamRecord(
                    id = UUID.randomUUID(),
                    competitionMatch = setupMatchId,
                    competitionRegistration = registrationId,
                    startNumber = 1,
                    place = null,
                    createdAt = seedTime,
                    updatedAt = seedTime,
                )
            )
            setupMatchId
        }
    }

    /** Kette einschalten - die Vorrichtung legt Veranstaltungen mit DEAKTIVIERT an. */
    private fun TestComprehensionScope<JEnv>.enableChain(eventId: UUID) {
        !EVENT.update(
            f = { chainProgressionMode = ChainProgressionMode.SCHIEDSRICHTER.name },
            condition = { ID.eq(eventId) },
        )
    }

    /**
     * Der Ausgangszustand des Vorfalls: Lauf 1 der ersten Runde ist gefahren und beendet, Lauf 2
     * steht am Start.
     *
     * [scoreLastMatch] entscheidet, ob Lauf 2 dabei schon durchgewertet ist. Mit `true` macht sein
     * Beenden die Runde vollständig und löst die Folgerunden-Automatik aus; mit `false` ist er ein
     * gerufener, aber noch nicht gefahrener Lauf - für die Kette ein offener Lauf, an dem sie
     * stehen bleiben muss (ein durchgewerteter wäre erledigt und dürfte übergangen werden).
     */
    private fun TestComprehensionScope<JEnv>.scoreFirstRound(
        seed: SeededRoundProgression,
        scoreLastMatch: Boolean = true,
    ) {
        seed.firstRoundMatchIds.forEachIndexed { index, matchId ->
            if (index == 0 || scoreLastMatch) {
                val teams = !COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(matchId) }
                teams.sortedBy { it.startNumber }.forEachIndexed { place, team ->
                    !COMPETITION_MATCH_TEAM.update(
                        f = { this.place = place + 1 },
                        condition = { ID.eq(team.id) },
                    )
                }
            }
            !COMPETITION_MATCH.update(
                f = {
                    // Nur der erste Lauf ist schon beendet; der zweite wird im Test beendet.
                    finishedAt = if (index == 0) seedTime else null
                    activatedAt = if (index == 0) null else seedTime
                },
                condition = { COMPETITION_SETUP_MATCH.eq(matchId) },
            )
        }
    }

    private fun TestComprehensionScope<JEnv>.activatedAt(setupMatchId: UUID): LocalDateTime? =
        (!COMPETITION_MATCH.select { COMPETITION_SETUP_MATCH.eq(setupMatchId) }).single().activatedAt

    // --- Der Vorfall vom 10.08.2026 ------------------------------------------------------------

    /**
     * Der Kern des Vorfalls, ohne jede Nebenläufigkeit: Der nächste Lauf steht schon am Start (von
     * Hand gerufen, von der Zeitnahme gemeldet oder von einem früheren Kettenlauf). Beendet jemand
     * den davor, darf die Kette ihn nicht überholen und den übernächsten dazuholen.
     */
    @Test
    fun aRaceAlreadyAtTheStartIsNotOvertaken() = testComprehension {
        val seed = seedTwoRoundCompetition()
        enableChain(seed.eventId)
        scoreFirstRound(seed, scoreLastMatch = false)

        val sideRaces = seedSideRaces(seed.eventId, count = 1)

        val running = slot(seed.eventId, on(day1, 10, 0), seed.firstRoundMatchIds[0])
        // Lauf 2 ist bereits an den Start gerufen (scoreFirstRound setzt activated_at) ...
        slot(seed.eventId, on(day1, 10, 10), seed.firstRoundMatchIds[1])
        // ... und dahinter steht ein fahrbereiter Lauf, der nichts von alledem mitbekommen soll.
        slot(seed.eventId, on(day1, 10, 20), sideRaces[0])

        // Lauf 1 wird gerade gefahren und jetzt beendet.
        !COMPETITION_MATCH.update(
            f = { finishedAt = null; activatedAt = seedTime; startedAt = seedTime },
            condition = { COMPETITION_SETUP_MATCH.eq(seed.firstRoundMatchIds[0]) },
        )

        !EventScheduleService.finishSlot(seed.eventId, running, SYSTEM_USER)

        assertNull(
            activatedAt(sideRaces[0]),
            "Der übernächste Lauf darf nicht an den Start gerufen werden, solange der nächste " +
                "dort noch steht",
        )
    }

    @Test
    fun oneFinishCallsOnlyOneStartGroupEvenWhenTheFollowingRoundIsCreated() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        enableChain(seed.eventId)
        scoreFirstRound(seed)

        val sideRaces = seedSideRaces(seed.eventId, count = 2)

        slot(seed.eventId, on(day1, 10, 0), seed.firstRoundMatchIds[0])
        val lastOfRound = slot(seed.eventId, on(day1, 10, 10), seed.firstRoundMatchIds[1])
        slot(seed.eventId, on(day1, 10, 20), sideRaces[0])
        slot(seed.eventId, on(day1, 10, 30), sideRaces[1])
        slot(seed.eventId, on(day2, 9, 0), seed.secondRoundSetupMatchId)

        !EventScheduleService.finishSlot(seed.eventId, lastOfRound, SYSTEM_USER)

        assertNotNull(
            activatedAt(sideRaces[0]),
            "Der nächste Lauf im Zeitplan hätte an den Start gerufen werden müssen",
        )
        assertNull(
            activatedAt(sideRaces[1]),
            "Der übernächste Lauf darf NICHT mitgerufen werden - er ist die Startgruppe zu viel, " +
                "die am 10.08.2026 in Flensburg aufgefallen ist",
        )
    }

    /**
     * Thomas' Vorgabe: "Es gibt keinen Grund, einen Lauf am nächsten Tag zu starten, wenn es noch
     * vorher weitere Rennen gibt."
     *
     * Der Aufbau ist der aus Flensburg: Ein Freilos der Folgerunde liegt am zweiten Tag und wird
     * quittiert (= beendet), während der erste Tag noch offene Rennen hat. Vorher aktivierte die
     * Kette daraufhin den Lauf dahinter - am zweiten Tag, mitten im ersten.
     */
    @Test
    fun theChainDoesNotJumpToTheNextDayWhileTheCurrentDayIsStillOpen() = testComprehension {
        val seed = seedTwoRoundCompetition()
        enableChain(seed.eventId)
        scoreFirstRound(seed)
        // Beide Läufe der ersten Runde sind durch - offen bleibt am ersten Tag nur das Rennen um
        // 18:00 Uhr.
        seed.firstRoundMatchIds.forEach { matchId ->
            !COMPETITION_MATCH.update(
                f = { finishedAt = seedTime; activatedAt = null },
                condition = { COMPETITION_SETUP_MATCH.eq(matchId) },
            )
        }

        val sideRaces = seedSideRaces(seed.eventId, count = 3)

        slot(seed.eventId, on(day1, 10, 0), seed.firstRoundMatchIds[0])
        slot(seed.eventId, on(day1, 10, 10), seed.firstRoundMatchIds[1])
        // Der Rest des ersten Tages steht noch aus ...
        slot(seed.eventId, on(day1, 18, 0), sideRaces[0])
        // ... am zweiten Tag wird ein Lauf beendet, dahinter steht der nächste.
        val nextDayFinish = slot(seed.eventId, on(day2, 9, 0), sideRaces[1])
        slot(seed.eventId, on(day2, 9, 10), sideRaces[2])
        !COMPETITION_MATCH.update(
            f = { activatedAt = seedTime; startedAt = seedTime },
            condition = { COMPETITION_SETUP_MATCH.eq(sideRaces[1]) },
        )

        !EventScheduleService.finishSlot(seed.eventId, nextDayFinish, SYSTEM_USER)

        assertNull(
            activatedAt(sideRaces[2]),
            "Am Folgetag darf nichts weiterlaufen, solange der erste Tag offene Rennen hat",
        )
        assertNotNull(
            activatedAt(sideRaces[0]),
            "Die Kette arbeitet den Zeitplan der Reihe nach ab - der offene Lauf des ersten Tages " +
                "ist der nächste",
        )
    }

    /**
     * Der Fall aus Thomas' Vorgabe: Vor dem nächsten fahrbereiten Lauf steht ein Slot, dessen Runde
     * noch nicht gesetzt ist. Dann wartet die Kette dort - sie überspringt ihn nicht.
     */
    @Test
    fun theChainWaitsAtAnUnsetRoundInsteadOfSkippingToALaterRace() = testComprehension {
        val seed = seedTwoRoundCompetition()
        enableChain(seed.eventId)
        scoreFirstRound(seed)

        val sideRaces = seedSideRaces(seed.eventId, count = 1)

        slot(seed.eventId, on(day1, 10, 0), seed.firstRoundMatchIds[0])
        val lastOfRound = slot(seed.eventId, on(day1, 10, 10), seed.firstRoundMatchIds[1])
        // Das Finale ist noch nicht gesetzt (keine Folgerunden-Automatik in diesem Test) ...
        slot(seed.eventId, on(day1, 10, 20), seed.secondRoundSetupMatchId)
        // ... und dahinter stünde ein fahrbereiter Lauf.
        slot(seed.eventId, on(day1, 10, 30), sideRaces[0])

        !EventScheduleService.finishSlot(seed.eventId, lastOfRound, SYSTEM_USER)

        assertNull(
            activatedAt(sideRaces[0]),
            "Vor dem ungesetzten Slot ist Schluss - die Kette wartet, bis die Runde steht",
        )
    }

    /** Der Regelfall bleibt: Ist der nächste Slot fahrbereit, wird er gerufen. */
    @Test
    fun theNextReadyRaceIsStillCalledToTheStart() = testComprehension {
        val seed = seedTwoRoundCompetition()
        enableChain(seed.eventId)
        scoreFirstRound(seed)

        val sideRaces = seedSideRaces(seed.eventId, count = 1)

        slot(seed.eventId, on(day1, 10, 0), seed.firstRoundMatchIds[0])
        val lastOfRound = slot(seed.eventId, on(day1, 10, 10), seed.firstRoundMatchIds[1])
        slot(seed.eventId, on(day1, 10, 20), sideRaces[0])

        !EventScheduleService.finishSlot(seed.eventId, lastOfRound, SYSTEM_USER)

        assertEquals(
            1,
            listOfNotNull(activatedAt(sideRaces[0])).size,
            "Der nächste Lauf hätte an den Start gerufen werden müssen",
        )
    }
}
