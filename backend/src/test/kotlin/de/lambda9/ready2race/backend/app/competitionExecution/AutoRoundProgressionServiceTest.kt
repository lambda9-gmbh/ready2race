package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.boundary.AutoRoundProgressionService
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionExecution.entity.UpdateCompetitionMatchResultRequest
import de.lambda9.ready2race.backend.app.competitionExecution.entity.UpdateCompetitionMatchTeamResultRequest
import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardService
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
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

    /**
     * Die acht Tests oben rufen alle direkt [AutoRoundProgressionService.progressIfRoundComplete]
     * auf - keiner davon geht über eine der fünf Stellen, an denen die Automatik tatsächlich
     * eingebaut ist. Ein vertauschter oder vergessener Aufruf an einer dieser Stellen würde von
     * keinem der acht Tests bemerkt. Dieser Test belegt stattdessen den Hauptweg von außen: Ein
     * Schiedsrichter beendet im Dashboard den letzten offenen Lauf der ersten Runde, und die
     * Folgerunde steht, ohne dass der Test die Automatik selbst anfasst.
     *
     * Von den übrigen vier Einbaustellen deckt `updateMatchResult` der Korrekturtest weiter unten
     * ab. `setSlotSkipped`, `updateMatchResultByFile` und `applyRaceClockerRows` bleiben bewusst
     * ohne eigenen Verdrahtungstest - das ist eine bekannte Lücke, kein Versehen.
     */
    @Test
    fun finishingTheLastMatchFromTheRefereeDashboardBringsTheNextRound() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))
        // Nur den zweiten Lauf wieder öffnen - der erste bleibt gewertet, damit das
        // Schiedsrichter-Dashboard gleich den letzten fehlenden Lauf beendet.
        !COMPETITION_MATCH.update(
            f = { finishedAt = null },
            condition = { COMPETITION_SETUP_MATCH.eq(seed.firstRoundMatchIds.last()) },
        )

        val beforeFinish = !COMPETITION_MATCH.select { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertEquals(0, beforeFinish.size, "Vor dem letzten Lauf darf es noch kein Finale geben")

        !LiveDashboardService.finishMatch(seed.eventId, seed.firstRoundMatchIds.last(), seed.userId)

        val finalMatch = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNotNull(finalMatch, "Das Finale hätte über das Dashboard-Beenden gesetzt werden müssen")
        assertNull(finalMatch.activatedAt)
        assertNull(finalMatch.startedAt)
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

    /**
     * Der Weg zur Korrektur: Folgerunde löschen, Ergebnis richtigstellen, Automatik rechnet neu.
     * Die neuen Paarungen tragen den Vermerk, an dem die Orga sieht, dass sich etwas verschoben hat.
     */
    @Test
    fun aCorrectionAfterDeletingRecreatesThePairingsWithANotice() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))
        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        !CompetitionExecutionService.deleteCurrentRound(seed.competitionId, seed.eventId)

        // Beleg, dass die Folgerunde nach dem Löschen wirklich weg ist - sonst prüfte der Test am
        // Ende bloß, dass etwas dasteht, das schon vor der Korrektur dastand.
        val afterDelete = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNull(afterDelete, "Die Folgerunde hätte nach dem Löschen weg sein müssen")

        // Die Plätze des ersten Laufs tauschen - so sieht eine Korrektur aus.
        val firstMatchId = seed.firstRoundMatchIds.first()
        val teams = (!COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(firstMatchId) }).sortedBy { it.startNumber }
        !CompetitionExecutionService.updateMatchResult(
            eventId = seed.eventId,
            competitionId = seed.competitionId,
            matchId = firstMatchId,
            userId = seed.userId,
            request = UpdateCompetitionMatchResultRequest(
                teamResults = listOf(
                    UpdateCompetitionMatchTeamResultRequest(
                        registrationId = teams[0].competitionRegistration!!,
                        place = 2,
                        timeString = null,
                        failed = false,
                        failedReason = null,
                        penaltySeconds = null,
                        penaltyNote = null,
                    ),
                    UpdateCompetitionMatchTeamResultRequest(
                        registrationId = teams[1].competitionRegistration!!,
                        place = 1,
                        timeString = null,
                        failed = false,
                        failedReason = null,
                        penaltySeconds = null,
                        penaltyNote = null,
                    ),
                )
            ),
        )

        val finalMatch = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNotNull(finalMatch, "Die Folgerunde hätte neu gesetzt werden müssen")
        assertNotNull(finalMatch.pairingsRecalculatedAt, "Die Neuberechnung hätte vermerkt werden müssen")
    }

    /**
     * Steht die Folgerunde bereits, ist die Runde davor gesperrt — dieselbe Antwort wie vor der
     * Automatik. Genau darauf beruht der Schutz gestarteter Läufe: Die Sperre hängt nicht am Start
     * eines Laufs, sondern schon daran, DASS es die Folgerunde gibt. `getCurrentAndNextRound`
     * erklärt jede Runde mit Läufen zur aktuellen, und an alles davor kommt niemand mehr heran.
     *
     * Ein gestarteter Lauf ist damit erst recht geschützt — er setzt die Existenz der Runde voraus.
     * Der Zeitstempel unten steht trotzdem im Test, weil er den Fall aus der Anforderung
     * ausbuchstabiert; tragend für den Ausgang ist er nicht.
     */
    @Test
    fun anExistingFollowingRoundLocksTheRoundBefore() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))
        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        // Der Lauf ist unterwegs. Für den Ausgang unerheblich (siehe oben), aber so sieht der Fall
        // aus, den die Anforderung meint.
        !COMPETITION_MATCH.update(
            f = { startedAt = LocalDateTime.of(2026, 8, 14, 11, 0) },
            condition = { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) },
        )

        // Über den echten Schreibweg geprüft, nicht über die interne Prüffunktion: Was zählt, ist
        // dass die Korrektur nicht durchgeht - nicht, dass eine private Hilfsfunktion nein sagt.
        val firstMatchId = seed.firstRoundMatchIds.first()
        val teams = (!COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(firstMatchId) }).sortedBy { it.startNumber }
        assertKIOFails(CompetitionExecutionError.MatchResultsLocked) {
            CompetitionExecutionService.updateMatchResult(
                eventId = seed.eventId,
                competitionId = seed.competitionId,
                matchId = firstMatchId,
                userId = seed.userId,
                request = UpdateCompetitionMatchResultRequest(
                    teamResults = listOf(
                        UpdateCompetitionMatchTeamResultRequest(
                            registrationId = teams[0].competitionRegistration!!,
                            place = 2,
                            timeString = null,
                            failed = false,
                            failedReason = null,
                            penaltySeconds = null,
                            penaltyNote = null,
                        ),
                        UpdateCompetitionMatchTeamResultRequest(
                            registrationId = teams[1].competitionRegistration!!,
                            place = 1,
                            timeString = null,
                            failed = false,
                            failedReason = null,
                            penaltySeconds = null,
                            penaltyNote = null,
                        ),
                    )
                ),
            )
        }

        // Und der Beleg, dass wirklich nichts passiert ist: die Plätze stehen wie vorher.
        val after = (!COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(firstMatchId) }).sortedBy { it.startNumber }
        assertEquals(listOf(1, 2), after.map { it.place })
    }
}
