package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.recoverDefault
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

/**
 * Der Auslöser der Folgerunden-Automatik: Nach jeder Änderung am Zustand eines Laufs wird gefragt,
 * ob dessen Runde damit durch ist — und wenn ja, die nächste gesetzt.
 *
 * Die Rechnung selbst steht unverändert in [CompetitionExecutionService.createNewRound] und ist
 * formatunabhängig. Dieser Service entscheidet ausschließlich, WANN sie läuft.
 *
 * Er aktiviert bewusst nichts: `createNewRound` stößt am Ende die Zeitstrahl-Kette an
 * (`ScheduleChainService.resumeIfParked`), und die entscheidet nach `chain_progression_mode`, ob
 * überhaupt ein Lauf an den Start gerufen wird. Steht die Veranstaltung auf DEAKTIVIERT, passiert
 * genau nichts — die Automatik fügt keine Aktivierung hinzu, die es vorher nicht gab.
 */
object AutoRoundProgressionService {

    private val logger = KotlinLogging.logger {}

    /**
     * Derselbe Ablauf, wenn nur der Lauf bekannt ist. Die Aufrufer aus dem Schiedsrichter-Dashboard
     * und aus dem Zeitplan kennen den Wettkampf nicht; ihn dort zu ermitteln hieße, dieselbe
     * Join-Kette an drei Stellen zu schreiben.
     */
    fun progressAfterMatch(eventId: UUID, matchId: UUID, userId: UUID): App<Nothing, Unit> =
        KIO.comprehension {
            val competitionId = !CompetitionMatchRepo.getCompetitionId(matchId).orDie()
                ?: return@comprehension KIO.unit

            progressIfRoundComplete(eventId, competitionId, userId)
        }

    /**
     * Dieselbe Frage, wenn nicht einmal die Veranstaltung feststeht: Der RaceClocker-Job
     * (`RaceClockerPollService`) und der geteilte Schreibweg dahinter (`applyRaceClockerRows`)
     * kennen nur den Lauf, keine Veranstaltung - die Job-Schleife dreht sich um Läufe, nicht um
     * Events, und sie mit einem zusätzlichen Parameter durch drei weitere Funktionen zu reichen,
     * nur damit diese Automatik ihn am Ende bekommt, wäre ein Umbau, den keine der Funktionen sonst
     * braucht. Wettkampf und Veranstaltung kommen deshalb hier aus derselben Kette, die auch
     * [progressAfterMatch] nutzt, nur einen Schritt weiter: Lauf -> Wettkampf -> Veranstaltung.
     */
    fun progressAfterMatch(matchId: UUID, userId: UUID): App<Nothing, Unit> =
        KIO.comprehension {
            val competitionId = !CompetitionMatchRepo.getCompetitionId(matchId).orDie()
                ?: return@comprehension KIO.unit
            val competition = !CompetitionRepo.getRecordById(competitionId).orDie()
                ?: return@comprehension KIO.unit

            progressIfRoundComplete(competition.event, competitionId, userId)
        }

    /**
     * Prüft und erzeugt. Jeder Abbruchgrund ist ein stilles `unit` — die Automatik ist eine
     * Bequemlichkeit obendrauf und darf den Aufrufer nie scheitern lassen.
     *
     * Die Idempotenz fällt strukturell an: `getCurrentAndNextRound` erklärt jede Runde, die schon
     * Läufe hat, zur aktuellen. Ein zweiter Aufruf betrachtet also die eben erzeugte Runde, findet
     * sie unbeendet und tut nichts. Es gibt keinen Pfad, auf dem dieselbe Runde zweimal entsteht.
     */
    fun progressIfRoundComplete(eventId: UUID, competitionId: UUID, userId: UUID): App<Nothing, Unit> =
        KIO.comprehension {
            val eventDefault = !EventRepo.getAutoCreateFollowingRounds(eventId).orDie()
            val override = !CompetitionRepo.getAutoCreateFollowingRounds(competitionId).orDie()
            if (!AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault, override)) {
                return@comprehension KIO.unit
            }

            // Wettkämpfe einer Challenge-Veranstaltung kennen keine Runden; createNewRound
            // verweigert dort ohnehin. EventRepo.isChallengeEvent liefert - anders als
            // EventService.checkIsChallengeEvent - keinen Fehlerkanal, sondern schlicht `null`, wenn
            // es die Veranstaltung nicht (mehr) gibt. Das behandelt diese Automatik wie jeden anderen
            // Abbruchgrund hier: still. Ein Defekt darf den Aufrufer nie mitreißen.
            val isChallenge = !EventRepo.isChallengeEvent(eventId).orDie() ?: false
            if (isChallenge) {
                return@comprehension KIO.unit
            }

            // Ein Wettkampf ohne Ablauf ist kein Fehler dieser Automatik, sondern schlicht keiner,
            // der Runden kennt.
            val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
                .recoverDefault { emptyList() }
            val (currentRound, nextRound) = CompetitionExecutionService.getCurrentAndNextRound(setupRounds)

            // Ohne aktuelle Runde gäbe es keine abgeschlossene Runde, aus der sich etwas ergeben
            // könnte: Die ERSTE Runde erzeugt weiterhin ein Mensch. Sie hängt an finalisierten
            // Meldungen, nicht an einem Rundenabschluss - und der Dialog dort macht bewusst darauf
            // aufmerksam, dass Meldungen ohne Startnummer unter den Tisch fallen.
            if (currentRound == null || nextRound == null) {
                return@comprehension KIO.unit
            }

            if (!AutoRoundProgressionLogic.roundIsComplete(currentRound)) {
                return@comprehension KIO.unit
            }

            // Scheitert die Erzeugung - etwa weil der Ablauf der nächsten Runde zu wenig Bahnen hat
            // -, darf das den Aufrufer nicht mitreißen. Der Lauf IST gefahren und beendet; das ist
            // eine Tatsache, die nicht an einer Setup-Lücke hängen darf. Der Knopf im
            // Durchführungs-Tab meldet denselben Fehler weiterhin sichtbar.
            !CompetitionExecutionService.createNewRound(eventId, competitionId, userId)
                .recoverDefault { error ->
                    logger.warn { "Folgerunde für Wettkampf $competitionId konnte nicht erzeugt werden: $error" }
                    ApiResponse.NoData
                }

            KIO.unit
        }
}
