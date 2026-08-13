package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameSettings
import de.lambda9.ready2race.backend.app.eventInfo.boundary.EventInfoService
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die „Letztes Ergebnis"-Kachel der Boards zeigt nur vom Schiedsrichter beendete Läufe —
 * unabhängig vom Sichtbarkeitsmodus der Veranstaltung. Ein voll gewerteter, unbeendeter Lauf
 * (AWAITING_FINISH) läuft auf dem Board noch in der „Im Rennen"-Kachel mit Live-Stand mit;
 * erschiene er zugleich als Ergebnis, doppelten sich die Kacheln (Nutzerwunsch 11.08.2026).
 * Die öffentliche Ergebnisseite behält dagegen ihre Visibility-Weiche unverändert — genau
 * diese Trennung wird hier an einer echten Datenbank belegt (`confirmedOnly` in
 * [EventInfoService.getLatestMatchResults]).
 */
class BoardResultConfirmationTest {

    /** Wertet alle Boote des Laufs: der Zieleinlauf ist damit komplett, beendet ist nichts. */
    private fun scoreTeams(matchId: UUID) = Jooq.query {
        update(COMPETITION_MATCH_TEAM)
            .set(COMPETITION_MATCH_TEAM.PLACE, 1)
            .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(matchId))
            .execute()
    }

    private fun setVisibility(eventId: UUID, visibility: String) = Jooq.query {
        update(EVENT)
            .set(EVENT.PUBLIC_RESULTS_VISIBILITY, visibility)
            .where(EVENT.ID.eq(eventId))
            .execute()
    }

    /** Die Schiedsrichter-Entscheidung: der Lauf ist beendet. */
    private fun finishMatch(matchId: UUID) = Jooq.query {
        update(COMPETITION_MATCH)
            .set(COMPETITION_MATCH.FINISHED_AT, LocalDateTime.now())
            .where(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(matchId))
            .execute()
    }

    private fun latestResults(eventId: UUID, confirmedOnly: Boolean) =
        EventInfoService.getLatestMatchResults(
            eventId,
            10,
            null,
            ClubShortNameSettings.none,
            confirmedOnly = confirmedOnly,
        )

    @Test
    fun boardResultsWaitForTheRefereeInBothVisibilityModes() = testComprehension {
        val fixture = !MyEventFixture.create()
        !scoreTeams(fixture.ownMatchId)

        // FINISHED_ONLY (Voreinstellung): der voll gewertete, unbeendete Lauf erscheint
        // nirgends als Ergebnis — weder öffentlich noch auf dem Board.
        val publicFinishedOnly = (!latestResults(fixture.eventId, confirmedOnly = false)).data
        assertFalse(publicFinishedOnly.any { it.matchId == fixture.ownMatchId })
        val boardFinishedOnly = (!latestResults(fixture.eventId, confirmedOnly = true)).data
        assertFalse(boardFinishedOnly.any { it.matchId == fixture.ownMatchId })

        // RESULTS_COMPLETE: die öffentliche Seite zeigt ihn (bisheriges Verhalten, unverändert),
        // die Board-Kachel wartet weiter auf die Schiedsrichter-Entscheidung.
        !setVisibility(fixture.eventId, "RESULTS_COMPLETE")
        val publicComplete = (!latestResults(fixture.eventId, confirmedOnly = false)).data
        assertTrue(publicComplete.any { it.matchId == fixture.ownMatchId })
        val boardComplete = (!latestResults(fixture.eventId, confirmedOnly = true)).data
        assertFalse(boardComplete.any { it.matchId == fixture.ownMatchId })

        // Erst das Beenden verschiebt den Lauf auch auf die Board-Kachel.
        !finishMatch(fixture.ownMatchId)
        val boardAfterFinish = (!latestResults(fixture.eventId, confirmedOnly = true)).data
        assertTrue(boardAfterFinish.any { it.matchId == fixture.ownMatchId })
    }

    // Der nie gewertete zweite Lauf der Halterung bleibt in jeder Konstellation draußen —
    // die Auswahl greift nicht versehentlich zu weit.
    @Test
    fun unscoredMatchesNeverAppear() = testComprehension {
        val fixture = !MyEventFixture.create()
        !setVisibility(fixture.eventId, "RESULTS_COMPLETE")
        val results = (!latestResults(fixture.eventId, confirmedOnly = false)).data
        assertFalse(results.any { it.matchId == fixture.foreignMatchId })
    }
}
