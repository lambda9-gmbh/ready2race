package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

/**
 * Setzt oder löscht den veranstaltungsweiten Hinweis — beide Felder gesetzt heißt setzen,
 * beide null heißt löschen. Bewusst ein eigener, schmaler Request und kein Feld am großen
 * [UpdateEventRequest]: der Handgriff am Renntag ("Wetterwarnung raus") soll nicht das ganze
 * Veranstaltungsformular mitschicken müssen (dasselbe Muster wie die timing-config-PUTs).
 *
 * [severity] kommt als String und wird hier gegen [EventNoticeSeverity] geprüft, statt Jackson
 * direkt in das Enum übersetzen zu lassen: ein unbekannter Wert soll als Validierungsfehler
 * (422) antworten, nicht als unparsbarer Rumpf (400).
 */
data class UpdateEventNoticeRequest(
    val text: String?,
    val severity: String?,
) : Validatable {

    override fun validate(): ValidationResult =
        when {
            // Beide null: Banner löschen - nichts weiter zu prüfen.
            text == null && severity == null -> ValidationResult.Valid

            // Halbe Zustände gibt es nicht, siehe Paar-Check in Migration V202608111700.
            text == null || severity == null ->
                ValidationResult.Invalid.Message { "text and severity must both be set or both be null" }

            text.isBlank() ->
                ValidationResult.Invalid.Message { "text must not be blank" }

            EventNoticeSeverity.entries.none { it.name == severity } ->
                ValidationResult.Invalid.Message {
                    "severity must be one of ${EventNoticeSeverity.entries.joinToString { it.name }}"
                }

            else -> ValidationResult.Valid
        }

    /** Die geprüfte Stufe als Enum; nur nach erfolgreicher Validierung aufrufen. */
    fun severityOrNull(): EventNoticeSeverity? = severity?.let { EventNoticeSeverity.valueOf(it) }

    companion object {
        val example
            get() = UpdateEventNoticeRequest(
                text = "Sturmwarnung: Start der Nachmittagsrennen verschiebt sich.",
                severity = EventNoticeSeverity.WARNING.name,
            )
    }
}
