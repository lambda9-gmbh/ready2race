package de.lambda9.ready2race.backend.app.event

import de.lambda9.ready2race.backend.app.event.entity.ExecutionAutoRefresh
import de.lambda9.ready2race.backend.app.event.entity.UpdateEventRequest
import de.lambda9.ready2race.backend.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Die Grenzen des automatischen Abgleichs auf der Durchführungsseite. Geprüft wird hier, weil sie
 * an drei Stellen gelten müssen (Request, Datenbank-Constraint, Formular) und nur eine davon ohne
 * laufende Anwendung sichtbar ist.
 */
class ExecutionAutoRefreshTest {

    private fun request(seconds: Int, enabled: Boolean = true) =
        UpdateEventRequest.example.copy(
            executionAutoRefresh = enabled,
            executionAutoRefreshSeconds = seconds,
        )

    @Test
    fun `interval within the bounds is accepted`() {
        assertEquals(ValidationResult.Valid, request(5).validate())
        assertEquals(ValidationResult.Valid, request(30).validate())
        assertEquals(ValidationResult.Valid, request(60).validate())
    }

    @Test
    fun `interval below the minimum is rejected`() {
        assertIs<ValidationResult.Invalid>(request(4).validate())
        assertIs<ValidationResult.Invalid>(request(0).validate())
        assertIs<ValidationResult.Invalid>(request(-5).validate())
    }

    @Test
    fun `interval above the maximum is rejected`() {
        assertIs<ValidationResult.Invalid>(request(61).validate())
        assertIs<ValidationResult.Invalid>(request(3600).validate())
    }

    /**
     * Der Takt bleibt gespeichert, während die Automatik aus ist - wer sie später einschaltet,
     * soll nicht auf einen Wert stoßen, den niemand geprüft hat.
     */
    @Test
    fun `interval is validated even while the sync is switched off`() {
        assertIs<ValidationResult.Invalid>(request(seconds = 1, enabled = false).validate())
    }

    @Test
    fun `the default interval lies within the bounds`() {
        assertEquals(
            ValidationResult.Valid,
            ExecutionAutoRefresh.validateSeconds(ExecutionAutoRefresh.DEFAULT_SECONDS),
        )
    }

    /** Der Text nennt die Grenzen, damit die Meldung im Formular ohne Nachschlagen taugt. */
    @Test
    fun `the message names the field and the bounds`() {
        val result = ExecutionAutoRefresh.validateSeconds(1, "executionAutoRefreshSeconds")

        assertIs<ValidationResult.Invalid.Message>(result)
        assertEquals(
            "executionAutoRefreshSeconds must be between 5 and 60 seconds",
            result.message(),
        )
    }
}
