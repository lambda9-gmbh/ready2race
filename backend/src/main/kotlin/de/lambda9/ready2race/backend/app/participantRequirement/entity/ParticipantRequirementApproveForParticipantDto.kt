package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import java.util.UUID

/**
 * Eine einzelne Bestätigung aus der Scan-App: genau eine Person, genau eine Bedingung.
 *
 * Bewusst getrennt von [ParticipantRequirementCheckForEventUpsertDto]: der dortige Weg
 * ersetzt den kompletten Zustand einer Bedingung (Massen-Pflege im Verwaltungs-UI mit
 * Transfer-Liste). Die Scan-App hat diesen Gesamtzustand nie in der Hand - riefe sie den
 * Ersetzen-Weg mit nur der gescannten Person auf, löschte der alle übrigen Bestätigungen
 * derselben Bedingung (der Waage-Vorfall vom Regattatag). Dieser Weg hier ist rein additiv
 * auf Datensatz-Ebene und rührt keine anderen Personen an.
 *
 * [competitionId] ist optional: eine Bedingung mit `perCompetition` braucht den Wettkampf,
 * für den die Bestätigung gilt. Ohne Angabe wird die Erfüllung ohne Wettkampfbezug
 * gespeichert - was bei eingeschaltetem Schalter bewusst keinen Lauf abdeckt (siehe
 * `RequirementScopeLogic.covers`).
 */
data class ParticipantRequirementApproveForParticipantDto(
    val requirementId: UUID,
    val participantId: UUID,
    val approved: Boolean,
    val note: String? = null,
    val namedParticipantId: UUID? = null,
    val competitionId: UUID? = null,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::requirementId validate notNull,
        this::participantId validate notNull,
        this::approved validate notNull,
    )

    companion object {
        val example
            get() = ParticipantRequirementApproveForParticipantDto(
                requirementId = UUID.randomUUID(),
                participantId = UUID.randomUUID(),
                approved = true,
                note = "unter Vorbehalt",
                namedParticipantId = null,
                competitionId = null,
            )
    }
}
