package de.lambda9.ready2race.backend.app.substitution.entity

import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord

data class SubstitutionsSharedFunctionData(
    val possibleSubstitutions: PossibleSubstitutionsForParticipantDto,
    val participant: ParticipantRecord,
    val participantsInClub: List<ParticipantRecord>,
    val registrationParticipants: List<ParticipantForExecutionDto>,
    val substitutions: List<SubstitutionViewRecord>,
)