package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import java.util.*

/**
 * Die Massen-Pflege einer Bedingung im Verwaltungs-UI: [approvedParticipants] ist der VOLLSTÄNDIGE
 * Zustand - wer fehlt, verliert seine Bestätigung.
 *
 * [competitionId] ist der Rahmen, in dem dieser Zustand gilt. Er wird gebraucht, seit eine
 * Bedingung je Wettkampf gelten kann (V202608141900): Ohne ihn löschte der Abgleich die
 * Bestätigungen aller anderen Wettkämpfe mit und schriebe neue ohne Wettkampfbezug - die deckten
 * dann bewusst keinen Lauf ab. Bei einer Bedingung ohne `perCompetition` bleibt er null.
 *
 * Der Tag steht bewusst NICHT im Aufruf: Bei `perEventDay` bestimmt ihn der Server aus dem
 * Zeitpunkt des Abgleichs, genau wie beim Scan an der Waage - zwei Quellen für "heute" wären eine
 * zu viel.
 */
data class ParticipantRequirementCheckForEventUpsertDto(
    val requirementId: UUID,
    val approvedParticipants: List<CheckedParticipantRequirement>,
    val namedParticipantId: UUID? = null,
    val competitionId: UUID? = null,

) : Validatable {
    override fun validate(): ValidationResult =
        this::approvedParticipants validate noDuplicates(CheckedParticipantRequirement::id)

    companion object {
        val example
            get() = ParticipantRequirementCheckForEventUpsertDto(
                requirementId = UUID.randomUUID(),
                approvedParticipants = listOf(
                    CheckedParticipantRequirement(
                        id = UUID.randomUUID(),
                        note = null
                    ),
                    CheckedParticipantRequirement(
                        id = UUID.randomUUID(),
                        note = "unter Vorbehalt"
                    )
                ),
                namedParticipantId = null,
                competitionId = null,
            )
    }
}