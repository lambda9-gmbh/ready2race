package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionChallengeError
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Meldungen der Durchführung. Geprüft wird nur die Zuordnung: dass jeder Grund, den ein Nutzer
 * bei der Ergebniserfassung auslösen kann, mit einem eigenen Code ankommt statt in einer
 * Sammelmeldung zu verschwinden.
 */
class ExecutionErrorTest {

    @Test
    fun everySubstitutionReasonHasItsOwnCode() {
        val codes = SubstitutionError.entries.map { it.respond().errorCode }
        assertTrue(codes.all { it != null }, "Jeder Grund braucht einen Code: $codes")
        assertEquals(SubstitutionError.entries.size, codes.toSet().size, "Codes müssen eindeutig sein")
    }

    /**
     * Die beiden Löschgründe kamen im Dashboard beide über "Status 409 oder nicht" an, obwohl der
     * eine sagt "lösch zuerst die späteren Ummeldungen" und der andere "das geht hier gar nicht,
     * sondern nur in der Runde von damals".
     */
    @Test
    fun theTwoDeletionReasonsAreToldApart() {
        val dependent = SubstitutionError.DependentSubstitutionFound.respond()
        val previousRound = SubstitutionError.CreatedInPreviousRound.respond()

        assertEquals(ErrorCode.SUBSTITUTION_DEPENDENT_FOUND, dependent.errorCode)
        assertEquals(ErrorCode.SUBSTITUTION_CREATED_IN_PREVIOUS_ROUND, previousRound.errorCode)
    }

    @Test
    fun everyChallengeReasonHasItsOwnCode() {
        val errors = listOf(
            CompetitionExecutionChallengeError.NotAChallengeEvent,
            CompetitionExecutionChallengeError.ChallengeAlreadyStarted,
            CompetitionExecutionChallengeError.ChallengeNotStartedYet,
            CompetitionExecutionChallengeError.CorruptedSetup,
            CompetitionExecutionChallengeError.ResultAlreadySubmitted,
            CompetitionExecutionChallengeError.NoResultSubmitted,
            CompetitionExecutionChallengeError.SelfSubmissionNotAllowed,
        )
        val codes = errors.map { it.respond().errorCode }

        assertTrue(codes.all { it != null }, "Jeder Grund braucht einen Code: $codes")
        assertEquals(errors.size, codes.toSet().size, "Codes müssen eindeutig sein")
    }

    @Test
    fun theFourExecutionReasonsCarryTheirOwnCodes() {
        assertEquals(
            ErrorCode.EXECUTION_MATCH_RESULTS_LOCKED,
            CompetitionExecutionError.MatchResultsLocked.respond().errorCode,
        )
        assertEquals(
            ErrorCode.EXECUTION_START_TIME_MANAGED_BY_SCHEDULE,
            CompetitionExecutionError.StartTimeManagedBySchedule.respond().errorCode,
        )
        assertEquals(
            ErrorCode.EXECUTION_TEAMS_NOT_MATCHING,
            CompetitionExecutionError.TeamsNotMatching.respond().errorCode,
        )
        assertEquals(
            ErrorCode.EXECUTION_PLACES_NOT_CONTINUOUS,
            CompetitionExecutionError.PlacesNotContinuous(expected = 3, actual = 4).respond().errorCode,
        )
    }

    /**
     * Ohne die beiden Zahlen muss der Nutzer die Ergebnisliste selbst durchzählen, um die Lücke zu
     * finden - bei einem Massenfeld ist das genau die Arbeit, die die Meldung abnehmen soll.
     */
    @Test
    fun theGapInThePlacesIsNamed() {
        val error = CompetitionExecutionError.PlacesNotContinuous(expected = 3, actual = 4).respond()

        assertEquals(3, error.details?.get("expected"))
        assertEquals(4, error.details?.get("actual"))
    }

    /**
     * Ein Freilos ("das Boot zieht ohne zu fahren weiter") lief bislang als MatchResultsLocked und
     * las sich damit als "nur die aktuelle Runde ist bearbeitbar" - die falsche Fährte. RaceClocker
     * trennt die beiden längst; die Ergebniserfassung zieht hier nach.
     */
    @Test
    fun aByeIsNotTheSameAsALockedEarlierRound() {
        val bye = CompetitionExecutionError.MatchIsBye.respond()
        val locked = CompetitionExecutionError.MatchResultsLocked.respond()

        assertEquals(ErrorCode.EXECUTION_MATCH_IS_BYE, bye.errorCode)
        assertTrue(bye.message != locked.message)
    }
}
