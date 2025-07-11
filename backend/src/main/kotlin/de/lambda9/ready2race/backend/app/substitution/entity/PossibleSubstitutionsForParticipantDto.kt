package de.lambda9.ready2race.backend.app.substitution.entity

data class PossibleSubstitutionsForParticipantDto(
    val currentlyParticipating: List<PossibleSubstitutionParticipantDto>,
    val notCurrentlyParticipating: List<PossibleSubstitutionParticipantDto>,
)