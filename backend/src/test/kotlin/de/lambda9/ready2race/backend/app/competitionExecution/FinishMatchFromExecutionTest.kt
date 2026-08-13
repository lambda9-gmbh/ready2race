package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.event.entity.ChainProgressionMode
import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardService
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardError
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Das Beenden von der Durchführungsseite aus (CompetitionExecutionService.finishMatch) muss in
 * JEDEM chainProgressionMode funktionieren — Nutzerentscheidung vom 12.08.2026: Der Hinweistext
 * der Einstellung verspricht dem Regattabüro das Eingreifen unabhängig vom Modus, bis dahin galt
 * das aber nur für den Zeitplan-Weg (finishSlot), und die Durchführungsseite hatte gar keinen
 * Beenden-Knopf. Gegen ein echtes Postgres, weil das Beenden durch den gemeinsamen Trichter
 * (finishMatchInternal) samt Ketten- und Markerlogik läuft.
 */
class FinishMatchFromExecutionTest {

    /** Die Vorrichtung legt Veranstaltungen mit dem Spalten-Default (DEAKTIVIERT) an. */
    private fun TestComprehensionScope<JEnv>.setMode(eventId: UUID, mode: ChainProgressionMode) {
        !EVENT.update(
            f = { chainProgressionMode = mode.name },
            condition = { ID.eq(eventId) },
        )
    }

    /** Der gemeldete Fall: SCHIEDSRICHTER-Modus, das Büro beendet über die Durchführungsseite. */
    @Test
    fun theOfficeFinishesInRefereeMode() = testComprehension {
        val seed = seedTwoRoundCompetition()
        setMode(seed.eventId, ChainProgressionMode.SCHIEDSRICHTER)
        val matchId = seed.firstRoundMatchIds.first()

        !CompetitionExecutionService.finishMatch(seed.eventId, matchId, seed.userId)

        val match = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(matchId) }
        assertNotNull(match?.finishedAt, "Der Lauf hätte beendet sein müssen")
    }

    /**
     * Auch im REGATTABUERO-Modus offen — dort ist das Dashboard-Beenden gesperrt (der Kontrast
     * unten), aber die Durchführungsseite ist gerade das Werkzeug des Büros und verhält sich wie
     * der Zeitplan-Weg.
     */
    @Test
    fun theOfficeFinishesInOfficeModeWhereTheDashboardIsGated() = testComprehension {
        val seed = seedTwoRoundCompetition()
        setMode(seed.eventId, ChainProgressionMode.REGATTABUERO)
        val (first, second) = seed.firstRoundMatchIds

        assertKIOFails(LiveDashboardError.FinishReservedForOffice) {
            LiveDashboardService.finishMatch(seed.eventId, first, seed.userId)
        }

        !CompetitionExecutionService.finishMatch(seed.eventId, second, seed.userId)

        val match = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(second) }
        assertNotNull(match?.finishedAt, "Der Lauf hätte beendet sein müssen")
    }
}
