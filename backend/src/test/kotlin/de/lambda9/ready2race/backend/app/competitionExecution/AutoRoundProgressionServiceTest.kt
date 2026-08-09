package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import kotlin.test.Test
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
}
