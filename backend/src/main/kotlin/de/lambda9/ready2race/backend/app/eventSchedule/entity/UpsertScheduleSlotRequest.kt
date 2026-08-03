package de.lambda9.ready2race.backend.app.eventSchedule.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.time.LocalDateTime
import java.util.UUID

data class UpsertScheduleSlotRequest(
    val startTime: LocalDateTime,
    val competitionSetupMatch: UUID?,
    val name: String?,
    val durationMinutes: Int?,
) : Validatable {

    // XOR: ein Slot zeigt entweder auf eine Setup-Zeile (Lauf-Slot) oder trägt einen Namen
    // (freier Slot), nie beides und nie keins von beiden (siehe chk_slot_match_xor_name).
    override fun validate(): ValidationResult = ValidationResult.allOf(
        if ((competitionSetupMatch == null) == (name == null)) {
            ValidationResult.Invalid.Message { "exactly one of competitionSetupMatch or name must be set" }
        } else {
            ValidationResult.Valid
        },
    )

    companion object {
        val example
            get() = UpsertScheduleSlotRequest(
                startTime = LocalDateTime.now(),
                competitionSetupMatch = null,
                name = "Pause",
                durationMinutes = 15,
            )
    }
}
