package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Nummerierung der Freilose einer Runde ([CompetitionExecutionService.byeNumbersForRound]) -
 * rein, ohne Datenbank. Der Normalfall (Setzungszahlen bekannt und eindeutig) läuft zusätzlich
 * integriert in [MatchNamingReapplicationTest]; hier stehen vor allem die Zweige, die dort nie
 * eintreten: fehlende oder doppelte Setzungen und die verpflichtende Runde.
 */
class ByeNumbersForRoundTest {

    private val matchIds = List(4) { UUID.randomUUID() }

    private fun round(required: Boolean) = CompetitionSetupRoundWithMatches(
        setupRoundId = UUID.randomUUID(),
        competitionSetup = UUID.randomUUID(),
        nextRound = null,
        setupRoundName = "Viertelfinale",
        required = required,
        isQualification = false,
        placesOption = "EQUAL",
        materializedAt = null,
        places = emptyList(),
        setupMatches = matchIds.mapIndexed { index, id ->
            CompetitionSetupMatchRecord(
                id = id,
                competitionSetupRound = UUID.randomUUID(),
                weighting = index + 1,
                teams = 2,
                name = "VF${index + 1}",
                executionOrder = index + 1,
            )
        },
        matches = emptyList(),
        substitutions = emptyList(),
    )

    @Test
    fun numbersAreTheSeedsOfTheRacingBoats() {
        // Setzung 1 und 3 haben ein Freilos - die Nummer ist die Setzungszahl, auch mit Lücke:
        // "Freilos 3" meint dieselbe Zahl, die der Freilos-Chip als Setzung zeigt.
        val numbers = CompetitionExecutionService.byeNumbersForRound(
            round(required = false),
            mapOf(
                matchIds[0] to listOf(1),
                matchIds[1] to listOf(2, 7),
                matchIds[2] to listOf(3),
                matchIds[3] to listOf(4, 5),
            ),
        )
        assertEquals(mapOf(matchIds[0] to 1, matchIds[2] to 3), numbers)
    }

    @Test
    fun missingSeedsFallBackToSequentialNumbersInWeightingOrder() {
        // Eine unbekannte Setzung genügt: dann werden ALLE Freilose fortlaufend nummeriert -
        // lieber lückenlos und deterministisch als halb Setzung, halb geraten.
        val numbers = CompetitionExecutionService.byeNumbersForRound(
            round(required = false),
            mapOf(
                matchIds[0] to listOf(4),
                matchIds[2] to listOf<Int?>(null),
                matchIds[3] to listOf(2),
            ),
        )
        assertEquals(mapOf(matchIds[0] to 1, matchIds[2] to 2, matchIds[3] to 3), numbers)
    }

    @Test
    fun duplicateSeedsFallBackToSequentialNumbers() {
        val numbers = CompetitionExecutionService.byeNumbersForRound(
            round(required = false),
            mapOf(
                matchIds[0] to listOf(2),
                matchIds[1] to listOf(2),
            ),
        )
        assertEquals(mapOf(matchIds[0] to 1, matchIds[1] to 2), numbers)
    }

    @Test
    fun aRequiredRoundHasNoByes() {
        // Dieselbe Regel wie MatchStatusLogic.deriveBye: in einer verpflichtenden Runde wird
        // auch ein Lauf mit nur einem Boot gefahren.
        val numbers = CompetitionExecutionService.byeNumbersForRound(
            round(required = true),
            mapOf(matchIds[0] to listOf(1)),
        )
        assertEquals(emptyMap<UUID, Int>(), numbers)
    }

    @Test
    fun matchesWithoutCreatedTeamsGetNoNumber() {
        // Nicht erzeugte Läufe (weniger Meldungen als Läufe) fehlen in der Karte - sie sind
        // keine Freilose, sie finden schlicht nicht statt.
        val numbers = CompetitionExecutionService.byeNumbersForRound(
            round(required = false),
            mapOf(matchIds[1] to listOf(1)),
        )
        assertEquals(mapOf(matchIds[1] to 1), numbers)
    }
}
