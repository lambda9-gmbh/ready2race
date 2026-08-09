package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.boundary.AutoRoundProgressionService
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Der Vermerk, an dem Admins und Schiedsrichter eine neu berechnete Paarung erkennen - gegen eine
 * echte Datenbank, weil die Unterscheidung "erste Erzeugung" gegen "Wiedererzeugung" an
 * `materialized_at` hängt, einer Spalte, die das Löschen der Runde überlebt (siehe
 * V202608091501).
 */
class AutoRoundProgressionServiceTest {

    /**
     * Die erste Erzeugung ist keine Wiederholung: Sie merkt sich die Runde, setzt aber keinen
     * Vermerk. Ein Hinweis „Paarung neu berechnet" am allerersten Finale wäre schlicht falsch.
     */
    @Test
    fun theFirstCreationLeavesNoNotice() = testComprehension {
        val seed = seedTwoRoundCompetition()
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))

        !CompetitionExecutionService.createNewRound(seed.eventId, seed.competitionId, seed.userId)

        val finalMatch = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNotNull(finalMatch, "Das Finale hätte gesetzt werden müssen")
        assertNull(finalMatch.pairingsRecalculatedAt)

        val round = !COMPETITION_SETUP_ROUND.selectOne { ID.eq(seed.secondRoundId) }
        assertNotNull(round?.materializedAt, "Die Runde hätte als gesetzt vermerkt werden müssen")
    }

    /**
     * Nach Löschen und Neuerzeugung trägt jeder Lauf den Vermerk. Genau daran erkennen Admins und
     * Schiedsrichter, dass sich unter ihnen etwas verschoben hat - die Runde sieht sonst aus wie
     * jede andere.
     */
    @Test
    fun aSecondCreationCarriesTheNotice() = testComprehension {
        val seed = seedTwoRoundCompetition()
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))

        !CompetitionExecutionService.createNewRound(seed.eventId, seed.competitionId, seed.userId)
        !CompetitionExecutionService.deleteCurrentRound(seed.competitionId, seed.eventId)
        !CompetitionExecutionService.createNewRound(seed.eventId, seed.competitionId, seed.userId)

        val finalMatch = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNotNull(finalMatch?.pairingsRecalculatedAt, "Der Vermerk hätte gesetzt sein müssen")
    }

    /** Der Regelfall: Ist die Runde durch, steht die nächste ohne Zutun. */
    @Test
    fun aFinishedRoundBringsTheNextOne() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))

        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        val finalMatch = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNotNull(finalMatch, "Das Finale hätte gesetzt werden müssen")
    }

    /**
     * Zweimal prüfen erzeugt nicht zweimal. Die Automatik hängt an fünf Auslösern, von denen
     * mehrere kurz hintereinander feuern können — doppelte Paarungen wären am Renntag nicht mehr
     * einzufangen.
     */
    @Test
    fun checkingTwiceCreatesTheRoundOnce() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))

        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)
        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        val matches = !COMPETITION_MATCH.select { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertEquals(1, matches.size)
    }

    /**
     * Erzeugen heißt nicht aufrufen. Ob und wann ein Lauf an den Start geht, entscheidet weiter
     * die Zeitstrahl-Kette — die Automatik darf dem nicht vorgreifen.
     */
    @Test
    fun theNewRoundIsNotActivated() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))

        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        val finalMatch = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNull(finalMatch?.activatedAt)
        assertNull(finalMatch?.startedAt)
    }

    /** Eine halbe Runde reicht nicht: ein unbeendeter Lauf hält alles an. */
    @Test
    fun anUnfinishedMatchHoldsEverything() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))
        // Den zweiten Lauf wieder öffnen - so sieht ein Zwischenstand aus.
        !COMPETITION_MATCH.update(
            f = { finishedAt = null },
            condition = { COMPETITION_SETUP_MATCH.eq(seed.firstRoundMatchIds.last()) },
        )

        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        val matches = !COMPETITION_MATCH.select { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertEquals(0, matches.size)
    }

    /** Ausgeschaltete Veranstaltung heißt: gar nichts passiert. */
    @Test
    fun theSettingOffCreatesNothing() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = false)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))

        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        val matches = !COMPETITION_MATCH.select { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertEquals(0, matches.size)
    }

    /** Der Wettkampf schlägt die Veranstaltung — in beide Richtungen. */
    @Test
    fun theCompetitionOverridesTheEvent() = testComprehension {
        val off = seedTwoRoundCompetition(eventAutoCreate = true, competitionAutoCreate = false)
        finishFirstRound(off, at = LocalDateTime.of(2026, 8, 14, 10, 30))
        !AutoRoundProgressionService.progressIfRoundComplete(off.eventId, off.competitionId, off.userId)
        assertEquals(0, (!COMPETITION_MATCH.select { COMPETITION_SETUP_MATCH.eq(off.secondRoundSetupMatchId) }).size)

        val on = seedTwoRoundCompetition(eventAutoCreate = false, competitionAutoCreate = true)
        finishFirstRound(on, at = LocalDateTime.of(2026, 8, 14, 10, 30))
        !AutoRoundProgressionService.progressIfRoundComplete(on.eventId, on.competitionId, on.userId)
        assertEquals(1, (!COMPETITION_MATCH.select { COMPETITION_SETUP_MATCH.eq(on.secondRoundSetupMatchId) }).size)
    }
}
