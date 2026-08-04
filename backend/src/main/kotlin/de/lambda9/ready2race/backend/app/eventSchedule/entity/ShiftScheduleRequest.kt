package de.lambda9.ready2race.backend.app.eventSchedule.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.time.LocalDateTime
import java.util.UUID

enum class ShiftMode { PLUS_MINUTES, SET_TIME, COMPRESS_TO_TARGET }

data class ShiftScheduleRequest(
    val fromSlotId: UUID,
    val mode: ShiftMode,
    val minutes: Long?,
    val newTime: LocalDateTime?,
    val targetSlotId: UUID?,
    val dryRun: Boolean,
) : Validatable {

    // Feldkombination hängt vom Modus ab: PLUS_MINUTES braucht nur minutes, SET_TIME nur newTime,
    // COMPRESS_TO_TARGET braucht targetSlotId plus genau eines von beiden (siehe Task-11-Brief).
    // Slot-bezogene Prüfungen (Ziel liegt hinter dem Start-Slot, delta <= 0) passieren erst im
    // Service, wo die Slot-Liste vorliegt.
    override fun validate(): ValidationResult = ValidationResult.allOf(
        when (mode) {
            ShiftMode.PLUS_MINUTES -> ValidationResult.allOf(
                requiredField(minutes != null, "minutes must be set for PLUS_MINUTES"),
                requiredField(newTime == null, "newTime must not be set for PLUS_MINUTES"),
                requiredField(targetSlotId == null, "targetSlotId must not be set for PLUS_MINUTES"),
            )

            ShiftMode.SET_TIME -> ValidationResult.allOf(
                requiredField(newTime != null, "newTime must be set for SET_TIME"),
                requiredField(minutes == null, "minutes must not be set for SET_TIME"),
                requiredField(targetSlotId == null, "targetSlotId must not be set for SET_TIME"),
            )

            ShiftMode.COMPRESS_TO_TARGET -> ValidationResult.allOf(
                requiredField(targetSlotId != null, "targetSlotId must be set for COMPRESS_TO_TARGET"),
                if ((minutes == null) == (newTime == null)) {
                    ValidationResult.Invalid.Message { "exactly one of minutes or newTime must be set for COMPRESS_TO_TARGET" }
                } else {
                    ValidationResult.Valid
                },
            )
        },
    )

    private fun requiredField(condition: Boolean, message: String): ValidationResult =
        if (condition) ValidationResult.Valid else ValidationResult.Invalid.Message { message }

    companion object {
        val example
            get() = ShiftScheduleRequest(
                fromSlotId = UUID.randomUUID(),
                mode = ShiftMode.PLUS_MINUTES,
                minutes = 15,
                newTime = null,
                targetSlotId = null,
                dryRun = true,
            )
    }
}

data class ShiftPreviewEntryDto(
    val slotId: UUID,
    val oldStartTime: LocalDateTime,
    val newStartTime: LocalDateTime,
)

data class ShiftPreviewDto(
    val entries: List<ShiftPreviewEntryDto>,
    val applied: Boolean,
)
