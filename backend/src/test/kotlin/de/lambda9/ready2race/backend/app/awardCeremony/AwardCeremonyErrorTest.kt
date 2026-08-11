package de.lambda9.ready2race.backend.app.awardCeremony

import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyError
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dieselbe Zusage wie bei den Urkunden ([de.lambda9.ready2race.backend.app.certificate.CertificateErrorTest]):
 * jeder Grund, den ein Nutzer auslösen kann, trägt einen eigenen Code, damit die Oberfläche
 * übersetzen statt raten muss.
 */
class AwardCeremonyErrorTest {

    @Test
    fun everyCeremonyReasonHasItsOwnCode() {
        val codes = AwardCeremonyError.entries.map { it.respond().errorCode }
        assertTrue(codes.all { it != null }, "Jeder Grund braucht einen Code: $codes")
        assertEquals(AwardCeremonyError.entries.size, codes.toSet().size, "Codes müssen eindeutig sein")
    }

    /**
     * Ein Challenge-Event bekommt nie eine Siegerehrung. Käme dort „keine Ergebnisse" zurück,
     * wartete das Büro auf Ergebnisse, die es nie geben wird - die beiden Fälle müssen sich für
     * das Frontend unterscheiden lassen.
     */
    @Test
    fun theChallengeEventReasonIsToldApartFromMissingResults() {
        val challenge = AwardCeremonyError.IsChallengeEvent.respond()
        val noResults = AwardCeremonyError.NoResults.respond()

        assertEquals(ErrorCode.AWARD_CEREMONY_IS_CHALLENGE_EVENT, challenge.errorCode)
        assertEquals(ErrorCode.AWARD_CEREMONY_NO_RESULTS, noResults.errorCode)
        assertTrue(challenge.message != noResults.message)
    }

    @Test
    fun ceremonyAndCertificateDoNotShareCodes() {
        // Sonst übersetzte das Urkunden-Modul im Frontend die Fälle der Siegerehrung mit.
        val ceremony = AwardCeremonyError.entries.mapNotNull { it.respond().errorCode }.toSet()
        val certificate = AwardCertificateError.entries.mapNotNull { it.respond().errorCode }.toSet()

        assertEquals(emptySet(), ceremony.intersect(certificate))
    }
}
