package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.event.entity.PublicResultsVisibility
import de.lambda9.ready2race.backend.app.eventInfo.boundary.EventInfoService
import de.lambda9.ready2race.backend.app.eventInfo.boundary.MyEventService
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Mein Event" lädt beim Antippen eines Ergebnisses das komplette Feld des Laufs nach — über
 * denselben Endpoint wie die öffentliche Ergebnisseite, nur auf einen Lauf eingegrenzt. Diese
 * Tests halten die Leitplanke fest: der Filter darf an `Event.publicResultsVisibility` nicht
 * vorbeikommen. Ein Lauf, den die Ergebnisseite zurückhält, kommt auch einzeln abgefragt als
 * leere Liste zurück.
 */
class MatchResultFieldTest {

    @Test
    fun unconfirmedMatchStaysHiddenUnderFinishedOnly() = testComprehension {
        // Alle Boote gewertet, aber der Lauf nicht beendet: bis zum Beenden kann noch eine
        // Zeitstrafe kommen, unter FINISHED_ONLY ist das noch kein öffentliches Ergebnis.
        val fixture = !MyEventFixture.create()
        !setVisibility(fixture.eventId, PublicResultsVisibility.FINISHED_ONLY)
        !scoreAllTeams(fixture.ownMatchId)

        val response = !EventInfoService.getLatestMatchResults(
            eventId = fixture.eventId,
            limit = 10,
            competitionId = null,
            matchId = fixture.ownMatchId,
        )
        assertTrue(response.data.isEmpty(), "Unbestätigter Lauf wurde ausgeliefert")
    }

    @Test
    fun scoredMatchAppearsUnderResultsComplete() = testComprehension {
        val fixture = !MyEventFixture.create()
        !setVisibility(fixture.eventId, PublicResultsVisibility.RESULTS_COMPLETE)
        !scoreAllTeams(fixture.ownMatchId)

        val response = !EventInfoService.getLatestMatchResults(
            eventId = fixture.eventId,
            limit = 10,
            competitionId = null,
            matchId = fixture.ownMatchId,
        )
        assertEquals(listOf(fixture.ownMatchId), response.data.map { it.matchId })
    }

    @Test
    fun finishedMatchDeliversItsFieldWithTheOwnTeam() = testComprehension {
        val fixture = !MyEventFixture.create()
        !setVisibility(fixture.eventId, PublicResultsVisibility.FINISHED_ONLY)
        !scoreAllTeams(fixture.ownMatchId)
        !finishMatch(fixture.ownMatchId)

        val response = !EventInfoService.getLatestMatchResults(
            eventId = fixture.eventId,
            limit = 10,
            competitionId = null,
            matchId = fixture.ownMatchId,
        )
        val match = response.data.single()
        assertEquals(fixture.ownMatchId, match.matchId)
        // Der Schlüssel, über den "Mein Event" das eigene Boot markiert.
        assertTrue(match.teams.any { it.teamId == fixture.ownRegistrationId })
        // Nur der angefragte Lauf: der fremde Lauf desselben Wettkampfs bleibt draußen.
        assertTrue(response.data.none { it.matchId == fixture.foreignMatchId })
    }

    @Test
    fun myEventResultCarriesTheTeamIdOfTheOwnBoat() = testComprehension {
        // Die Gegenstelle im persönlichen Dashboard: das Ergebnis trägt die eigene Meldung,
        // damit das nachgeladene Feld das eigene Boot wiederfindet.
        val fixture = !MyEventFixture.create()
        !scoreAllTeams(fixture.ownMatchId)
        !finishMatch(fixture.ownMatchId)

        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        val result = response.dto.results.single { it.matchId == fixture.ownMatchId }
        assertEquals(fixture.ownRegistrationId, result.teamId)
    }

    private fun setVisibility(eventId: UUID, visibility: PublicResultsVisibility): JIO<Int> =
        Jooq.query {
            update(EVENT)
                .set(EVENT.PUBLIC_RESULTS_VISIBILITY, visibility.name)
                .where(EVENT.ID.eq(eventId))
                .execute()
        }

    private fun scoreAllTeams(matchId: UUID): JIO<Int> = Jooq.query {
        update(COMPETITION_MATCH_TEAM)
            .set(COMPETITION_MATCH_TEAM.PLACE, 1)
            .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(matchId))
            .execute()
    }

    private fun finishMatch(matchId: UUID): JIO<Int> = Jooq.query {
        update(COMPETITION_MATCH)
            .set(COMPETITION_MATCH.FINISHED_AT, LocalDateTime.now())
            .where(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(matchId))
            .execute()
    }
}
