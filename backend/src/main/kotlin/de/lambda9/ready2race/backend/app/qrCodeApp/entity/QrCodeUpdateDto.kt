package de.lambda9.ready2race.backend.app.qrCodeApp.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import java.util.*

sealed class QrCodeUpdateDto() : Validatable {
    data class QrCodeAppuserUpdate(
        val id: UUID,
        val qrCodeId: String,
        val eventId: UUID,
    ) : QrCodeUpdateDto() {
        override fun validate(): ValidationResult = ValidationResult.allOf(
            this::qrCodeId validate notBlank
        )

        companion object {
            val example
                get() = QrCodeAppuserUpdate(
                    id = UUID.randomUUID(),
                    qrCodeId = UUID.randomUUID().toString(),
                    eventId = UUID.randomUUID(),
                )
        }
    }

    data class QrCodeParticipantUpdate(
        val id: UUID,
        val qrCodeId: String,
        val eventId: UUID,
    ) : QrCodeUpdateDto() {
        override fun validate(): ValidationResult = ValidationResult.allOf(
            this::qrCodeId validate notBlank
        )

        companion object {
            val example
                get() = QrCodeParticipantUpdate(
                    id = UUID.randomUUID(),
                    qrCodeId = UUID.randomUUID().toString(),
                    eventId = UUID.randomUUID(),
                )
        }
    }
}
