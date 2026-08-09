package de.lambda9.ready2race.backend.app.participantTracking.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import java.time.LocalDateTime

/**
 * Ein Eintrag von Hand - für den Nachtrag wie für die Korrektur, beide haben dieselbe Gestalt.
 *
 * [scannedAt] ist bewusst frei und nicht an `now()` gebunden: der Nachtrag betrifft naturgemäß
 * einen Zeitpunkt, der vorbei ist, und eine vorausschauende Eintragung zu verbieten hätte am Steg
 * keinen Nutzen.
 *
 * [reason] ist Pflicht - hier durch den Validator, in der Datenbank noch einmal durch
 * `check (length(btrim(reason)) > 0)`. Doppelt, weil eine Änderung ohne Begründung genau das
 * wäre, was diese Funktion nicht sein darf: ein stiller Eingriff ins Protokoll.
 */
data class ManualTrackingRequest(
    val scanType: ParticipantScanType,
    val scannedAt: LocalDateTime,
    val reason: String,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::reason validate notBlank,
    )

    companion object {
        val example
            get() = ManualTrackingRequest(
                scanType = ParticipantScanType.EXIT,
                scannedAt = LocalDateTime.of(2026, 8, 14, 9, 35),
                reason = "Boot ohne Scan abgelegt, Crew war auf dem Wasser",
            )
    }
}
