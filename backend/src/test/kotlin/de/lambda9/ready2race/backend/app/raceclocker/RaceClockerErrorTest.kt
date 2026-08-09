package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RaceClocker ist ein Bereich, den Schiedsrichter am Steg ohne Rückfragemöglichkeit bedienen - jeder
 * Fehlgriff (fehlende URL, doppelt importierter Start, laufender Lauf ohne Ergebnis, ...) braucht
 * einen eigenen, eindeutigen Code und Text, damit das Frontend übersetzen statt raten kann. Anders
 * als bei den enum-basierten Fehlern (siehe LiveDisplayErrorTest/CertificateErrorTest) ist
 * RaceClockerError ein sealed interface mit data classes, daher wird jede Ausprägung hier von Hand
 * gebaut statt über `.entries` aufgezählt.
 */
class RaceClockerErrorTest {

    private val allErrors: List<RaceClockerError> = listOf(
        RaceClockerError.UrlMissing,
        RaceClockerError.UrlInvalid("https://raceclocker.com/xxxx"),
        RaceClockerError.Unreachable("https://raceclocker.com/xxxx", "HTTP 500"),
        RaceClockerError.MalformedFeed("not valid JSON"),
        RaceClockerError.MatchNotInFeed(listOf("https://raceclocker.com/xxxx"), listOf("Kurzstrecke")),
        RaceClockerError.DuplicateTeams("AF1 CM1x", listOf("Boot A", "Boot B")),
        RaceClockerError.NoResults("AF1 CM1x"),
        RaceClockerError.MatchIsBye,
    )

    @Test
    fun everyReasonHasItsOwnCode() {
        val codes = allErrors.map { it.respond().errorCode }
        assertTrue(codes.all { it != null }, "Jeder Grund braucht einen Code: $codes")
        assertEquals(allErrors.size, codes.toSet().size, "Codes müssen eindeutig sein")
    }

    @Test
    fun everyReasonHasItsOwnMessage() {
        val messages = allErrors.map { it.respond().message }
        assertTrue(messages.all { it.isNotBlank() }, "Jeder Grund braucht einen Text: $messages")
        assertEquals(allErrors.size, messages.toSet().size, "Texte müssen eindeutig sein")
    }

    @Test
    fun codesMatchTheExpectedErrorCodeEnumValues() {
        assertEquals(ErrorCode.RACECLOCKER_URL_MISSING, RaceClockerError.UrlMissing.respond().errorCode)
        assertEquals(
            ErrorCode.RACECLOCKER_URL_INVALID,
            RaceClockerError.UrlInvalid("https://raceclocker.com/xxxx").respond().errorCode,
        )
        assertEquals(
            ErrorCode.RACECLOCKER_UNREACHABLE,
            RaceClockerError.Unreachable("https://raceclocker.com/xxxx", "HTTP 500").respond().errorCode,
        )
        assertEquals(
            ErrorCode.RACECLOCKER_MALFORMED_FEED,
            RaceClockerError.MalformedFeed("not valid JSON").respond().errorCode,
        )
        assertEquals(
            ErrorCode.RACECLOCKER_MATCH_NOT_IN_FEED,
            RaceClockerError.MatchNotInFeed(listOf("https://raceclocker.com/xxxx"), listOf("Kurzstrecke")).respond().errorCode,
        )
        assertEquals(
            ErrorCode.RACECLOCKER_DUPLICATE_TEAMS,
            RaceClockerError.DuplicateTeams("AF1 CM1x", listOf("Boot A")).respond().errorCode,
        )
        assertEquals(ErrorCode.RACECLOCKER_NO_RESULTS, RaceClockerError.NoResults("AF1 CM1x").respond().errorCode)
        assertEquals(ErrorCode.RACECLOCKER_MATCH_IS_BYE, RaceClockerError.MatchIsBye.respond().errorCode)
    }

    /**
     * "Kein Ergebnis" (Lauf läuft noch) und "Bye" (es gibt gar keinen Lauf) fühlen sich für ein Team
     * ohne Kontext ähnlich an ("nichts zu holen"), verlangen aber ein unterschiedliches Verhalten -
     * einmal später erneut ziehen, einmal gar nicht erst versuchen.
     */
    @Test
    fun noResultsIsToldApartFromMatchIsBye() {
        val noResults = RaceClockerError.NoResults("AF1 CM1x").respond()
        val bye = RaceClockerError.MatchIsBye.respond()

        assertTrue(noResults.errorCode != bye.errorCode)
        assertTrue(noResults.message != bye.message)
    }
}
