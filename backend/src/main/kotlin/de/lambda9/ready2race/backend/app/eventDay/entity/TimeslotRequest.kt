package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import java.time.LocalTime
import java.util.UUID

data class TimeslotRequest(
    val eventDay: UUID,
    val name: String,
    val description: String?,
    val startTime: LocalTime,
    val endTime: LocalTime
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::name validate notBlank,
        this::description validate notBlank,
    )

    companion object {
        val example get() = TimeslotRequest(
            eventDay = UUID.randomUUID(),
            name = "Debug session",
            description = "Timeslot for debugging",
            startTime = LocalTime.of(6, 0),
            endTime = LocalTime.of(23, 59)
        )
    }
}
