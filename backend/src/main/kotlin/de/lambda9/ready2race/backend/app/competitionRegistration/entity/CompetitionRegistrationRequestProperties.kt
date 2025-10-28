package de.lambda9.ready2race.backend.app.competitionRegistration.entity

import de.lambda9.ready2race.backend.app.invoice.entity.RegistrationInvoiceType
import java.util.UUID

sealed interface CompetitionRegistrationRequestProperties {

    data object None : CompetitionRegistrationRequestProperties

    data class Permitted(
        val registrationType: RegistrationInvoiceType,
    ) : CompetitionRegistrationRequestProperties

}