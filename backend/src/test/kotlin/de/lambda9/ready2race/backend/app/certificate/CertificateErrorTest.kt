package de.lambda9.ready2race.backend.app.certificate

import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateError
import de.lambda9.ready2race.backend.app.certificate.entity.CertificateError
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Urkunden-Fehler hatten als einziger Bereich 0 % ErrorCode-Abdeckung; der
 * Teilnahmeurkunden-Download reichte seinen englischen Backend-Text deshalb roh in die Oberfläche
 * durch. Hier wird nur die Zuordnung geprüft — dass jeder nutzerauslösbare Grund einen eigenen,
 * eindeutigen Code trägt, damit das Frontend übersetzen statt raten kann.
 */
class CertificateErrorTest {

    @Test
    fun everyParticipationCertificateReasonHasItsOwnCode() {
        val codes = CertificateError.entries.map { it.respond().errorCode }
        assertTrue(codes.all { it != null }, "Jeder Grund braucht einen Code: $codes")
        assertEquals(CertificateError.entries.size, codes.toSet().size, "Codes müssen eindeutig sein")
        assertEquals(ErrorCode.CERTIFICATE_NO_RESULTS, CertificateError.NoResults.respond().errorCode)
    }

    @Test
    fun everyAwardCertificateReasonHasItsOwnCode() {
        val codes = AwardCertificateError.entries.map { it.respond().errorCode }
        assertTrue(codes.all { it != null }, "Jeder Grund braucht einen Code: $codes")
        assertEquals(AwardCertificateError.entries.size, codes.toSet().size, "Codes müssen eindeutig sein")
    }

    /**
     * G18: Ein Siegerurkunden-Download auf einem Challenge-Event antwortete mit NoResults ("No
     * placed teams for these certificates"). Das Ergebnis stimmte zufällig, die Begründung nicht —
     * ein Challenge-Event fährt keine Läufe und vergibt keine Plätze, dort gibt es grundsätzlich
     * keine Siegerurkunden. Wer "keine platzierten Teams" liest, wartet auf Ergebnisse, die nie
     * kommen. Der Fall braucht deshalb eine eigene, unterscheidbare Antwort.
     */
    @Test
    fun theChallengeEventReasonIsToldApartFromMissingResults() {
        val challenge = AwardCertificateError.IsChallengeEvent.respond()
        val noResults = AwardCertificateError.NoResults.respond()

        assertEquals(ErrorCode.AWARD_CERTIFICATE_IS_CHALLENGE_EVENT, challenge.errorCode)
        assertEquals(ErrorCode.AWARD_CERTIFICATE_NO_RESULTS, noResults.errorCode)
        assertTrue(challenge.message != noResults.message)
    }

    /**
     * Die zwei Vorlagen-Probleme teilen sich denselben HTTP-Status (409) und waren dadurch im
     * Dialog nicht auseinanderzuhalten - "keine Vorlage hinterlegt" verlangt aber etwas anderes
     * als "die hinterlegte Vorlage ist kaputt".
     */
    @Test
    fun missingAndUnreadableTemplateShareAStatusButNotACode() {
        val missing = AwardCertificateError.MissingTemplate.respond()
        val unreadable = AwardCertificateError.UnreadableTemplate.respond()

        assertEquals(missing.status, unreadable.status)
        assertTrue(missing.errorCode != unreadable.errorCode)
    }

    @Test
    fun everyUserTriggerableTemplateReasonHasItsOwnCode() {
        val userTriggerable = GapDocumentTemplateError.entries - GapDocumentTemplateError.NotFound
        val codes = userTriggerable.map { it.respond().errorCode }
        assertTrue(codes.all { it != null }, "Jeder Grund braucht einen Code: $codes")
        assertEquals(userTriggerable.size, codes.toSet().size, "Codes müssen eindeutig sein")

        assertEquals(
            ErrorCode.DOCUMENT_TEMPLATE_INVALID_FONT,
            GapDocumentTemplateError.InvalidFont.respond().errorCode,
        )
        // NotFound bleibt bewusst ohne Code: eine 404 auf eine ID, die die Oberfläche gar nicht
        // anbietet, kann ein Nutzer über die normale Bedienung nicht auslösen.
        assertNull(GapDocumentTemplateError.NotFound.respond().errorCode)
    }

    @Test
    fun theTwoCertificateKindsDoNotShareCodes() {
        // Sonst würde ein Frontend-Modul den Fall des jeweils anderen Downloads mit übersetzen.
        val participation = CertificateError.entries.mapNotNull { it.respond().errorCode }.toSet()
        val award = AwardCertificateError.entries.mapNotNull { it.respond().errorCode }.toSet()

        assertEquals(emptySet(), participation.intersect(award))
    }
}
