package de.lambda9.ready2race.backend.app.event

import de.lambda9.ready2race.backend.app.event.entity.EventNoticeSeverity
import de.lambda9.ready2race.backend.app.event.entity.UpdateEventNoticeRequest
import de.lambda9.ready2race.backend.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Die Regeln des Hinweis-Requests, ohne Datenbank: was hier `Invalid` ist, beantwortet der
 * Server als 422 (BodyValidationFailed) - der HTTP-Weg dazu steht in `EventNoticeHttpIT`.
 */
class UpdateEventNoticeRequestTest {

    @Test
    fun settingTextAndSeverityIsValid() {
        assertEquals(
            ValidationResult.Valid,
            UpdateEventNoticeRequest(text = "Sturmwarnung", severity = "CRITICAL").validate(),
        )
    }

    @Test
    fun clearingWithBothNullIsValid() {
        val request = UpdateEventNoticeRequest(text = null, severity = null)
        assertEquals(ValidationResult.Valid, request.validate())
        assertNull(request.severityOrNull())
    }

    @Test
    fun anUnknownSeverityIsInvalid() {
        assertIs<ValidationResult.Invalid>(
            UpdateEventNoticeRequest(text = "Sturmwarnung", severity = "BANANE").validate(),
        )
    }

    @Test
    fun aBlankTextIsInvalidWhenSetting() {
        assertIs<ValidationResult.Invalid>(
            UpdateEventNoticeRequest(text = "   ", severity = "INFO").validate(),
        )
    }

    /** Halbe Zustände (nur Text oder nur Stufe) gibt es nicht - siehe Paar-Check der Migration. */
    @Test
    fun halfSetStatesAreInvalid() {
        assertIs<ValidationResult.Invalid>(
            UpdateEventNoticeRequest(text = "Sturmwarnung", severity = null).validate(),
        )
        assertIs<ValidationResult.Invalid>(
            UpdateEventNoticeRequest(text = null, severity = "INFO").validate(),
        )
    }

    @Test
    fun everyKnownSeverityPasses() {
        EventNoticeSeverity.entries.forEach { severity ->
            val request = UpdateEventNoticeRequest(text = "Hinweis", severity = severity.name)
            assertEquals(ValidationResult.Valid, request.validate(), severity.name)
            assertEquals(severity, request.severityOrNull())
        }
    }
}
