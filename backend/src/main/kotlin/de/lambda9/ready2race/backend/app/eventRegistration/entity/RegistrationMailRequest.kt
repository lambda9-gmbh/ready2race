package de.lambda9.ready2race.backend.app.eventRegistration.entity

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import java.util.UUID

/**
 * Die Rundmail, wie sie im Dialog getippt wurde. [subject] und [body] dürfen die Platzhalter
 * `##recipient##`, `##club##` und `##event##` enthalten; sie werden je Empfänger ersetzt.
 *
 * [registrationIds] sind die angehakten Meldungen, [additionalAddresses] die von Hand ergänzten
 * Adressen. Die Anhänge kommen als eigene Teile der Multipart-Anfrage und stehen deshalb nicht
 * hier.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RegistrationMailRequest(
    val subject: String,
    val body: String,
    val registrationIds: List<UUID>,
    val additionalAddresses: List<String>,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::subject validate notBlank,
            this::body validate notBlank,
        )

    companion object {
        val example
            get() = RegistrationMailRequest(
                subject = "Hinweise zum Regattatag",
                body = "Moin ##recipient##,\n\nbitte denkt an die Waage.",
                registrationIds = emptyList(),
                additionalAddresses = emptyList(),
            )
    }
}
