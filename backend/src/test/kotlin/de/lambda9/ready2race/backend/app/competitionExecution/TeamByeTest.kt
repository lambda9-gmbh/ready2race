package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionExecution.entity.UpdateTeamByeRequest
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Das vergebene Freilos (V202608151900): Ein Boot verpasst seinen Vorlauf, die Schiedsrichter
 * setzen es trotzdem in die Folgerunde.
 *
 * Der Behelf davor war, ihm im Vorlauf einen Platz einzutragen. Der stand dann im Ergebnis, in
 * der Platzberechnung und auf der Urkunde, und alle Boote, die wirklich gefahren sind, rutschten
 * eine Position nach hinten. Diese Tests halten beides fest: dass das Freilos aufsteigt und dass
 * es das Vorlaufergebnis in Ruhe lässt.
 */
class TeamByeTest {

    private val gestartet: LocalDateTime = LocalDateTime.of(2026, 8, 15, 11, 0)

    @Test
    fun aByeAdvancesWithoutPlaceOrTime() = testComprehension {
        val seed = seedTwoRoundCompetition()
        finishFirstRound(seed, gestartet)

        // Das zweitplatzierte Boot des ersten Vorlaufs - ohne Freilos käme nur der Sieger weiter.
        val ersterLauf = seed.firstRoundMatchIds.first()
        val zweiter = !COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(ersterLauf) }
            .map { teams -> teams.single { it.place == 2 } }

        !CompetitionExecutionService.updateTeamBye(
            eventId = seed.eventId,
            competitionId = seed.competitionId,
            matchId = ersterLauf,
            userId = seed.userId,
            request = UpdateTeamByeRequest(registrationId = zweiter.competitionRegistration!!, bye = true),
        )

        // Der Platz ist weg: Ein Freilos hat nichts gefahren, was zu werten wäre.
        val nachFreilos = !COMPETITION_MATCH_TEAM.selectOne { ID.eq(zweiter.id) }
        assertNotNull(nachFreilos)
        assertTrue(nachFreilos.bye == true)
        assertNull(nachFreilos.place, "ein Freilos bekommt keinen Platz")
        assertNull(nachFreilos.timecode, "ein Freilos bekommt keine Zeit")

        !CompetitionExecutionService.createNewRound(seed.eventId, seed.competitionId, seed.userId)

        val finale = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNotNull(finale, "das Finale hätte gesetzt werden müssen")

        val imFinale = !COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(seed.secondRoundSetupMatchId) }
            .map { teams -> teams.mapNotNull { it.competitionRegistration } }
        assertTrue(
            zweiter.competitionRegistration in imFinale,
            "das Boot mit Freilos gehört ins Finale, obwohl es keinen Platz hat",
        )
    }

    /**
     * Die fehlende Wertung darf die Runde nicht festfahren: Ohne die Ausnahme in der
     * Platz-Prüfung ("nicht alle Plätze gesetzt") wäre der Wettkampf genau dann blockiert, wenn
     * das Freilos gebraucht wird.
     */
    @Test
    fun theOtherBoatsKeepTheirPlaces() = testComprehension {
        val seed = seedTwoRoundCompetition()
        finishFirstRound(seed, gestartet)

        val ersterLauf = seed.firstRoundMatchIds.first()
        val zweiter = !COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(ersterLauf) }
            .map { teams -> teams.single { it.place == 2 } }

        !CompetitionExecutionService.updateTeamBye(
            eventId = seed.eventId,
            competitionId = seed.competitionId,
            matchId = ersterLauf,
            userId = seed.userId,
            request = UpdateTeamByeRequest(registrationId = zweiter.competitionRegistration!!, bye = true),
        )

        val restlichePlaetze = !COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(ersterLauf) }
            .map { teams -> teams.filter { it.bye != true }.mapNotNull { it.place }.sorted() }
        assertEquals(listOf(1), restlichePlaetze, "der Sieger bleibt Erster, nichts rutscht nach")
    }

    /** Zurücknehmen ist möglich, solange die Folgerunde noch nicht gesetzt ist. */
    @Test
    fun aByeCanBeRevokedBeforeTheNextRoundIsSeeded() = testComprehension {
        val seed = seedTwoRoundCompetition()
        finishFirstRound(seed, gestartet)

        val ersterLauf = seed.firstRoundMatchIds.first()
        val zweiter = !COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(ersterLauf) }
            .map { teams -> teams.single { it.place == 2 } }
        val request = { bye: Boolean ->
            UpdateTeamByeRequest(registrationId = zweiter.competitionRegistration!!, bye = bye)
        }

        !CompetitionExecutionService.updateTeamBye(
            seed.eventId, seed.competitionId, ersterLauf, seed.userId, request(true)
        )
        !CompetitionExecutionService.updateTeamBye(
            seed.eventId, seed.competitionId, ersterLauf, seed.userId, request(false)
        )

        val zurueck = !COMPETITION_MATCH_TEAM.selectOne { ID.eq(zweiter.id) }
        assertEquals(false, zurueck?.bye)
        assertNull(zurueck?.place, "der gelöschte Platz kommt nicht von selbst zurück")
    }

    /**
     * Sobald die Folgerunde gesetzt ist, ist die Setzung aus diesem Stand gezogen - ein
     * nachträgliches Freilos änderte daran nichts und würde das Gegenteil suggerieren. Dieselbe
     * Sperre wie beim Zurücksetzen eines Laufs.
     */
    @Test
    fun aByeIsRefusedOnceTheNextRoundExists() = testComprehension {
        val seed = seedTwoRoundCompetition()
        finishFirstRound(seed, gestartet)
        !CompetitionExecutionService.createNewRound(seed.eventId, seed.competitionId, seed.userId)

        val ersterLauf = seed.firstRoundMatchIds.first()
        val sieger = !COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(ersterLauf) }
            .map { teams -> teams.single { it.place == 1 } }

        assertKIOFails(CompetitionExecutionError.ResetBlockedByNextRound) {
            CompetitionExecutionService.updateTeamBye(
                eventId = seed.eventId,
                competitionId = seed.competitionId,
                matchId = ersterLauf,
                userId = seed.userId,
                request = UpdateTeamByeRequest(registrationId = sieger.competitionRegistration!!, bye = true),
            )
        }
    }
}
