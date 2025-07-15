package de.lambda9.ready2race.backend.app.substitution.control

import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionHasParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.SUBSTITUTION_HAS_PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.insert

object SubstitutionHasParticipantRequirementRepo {

    fun insert(records: List<SubstitutionHasParticipantRequirementRecord>) = SUBSTITUTION_HAS_PARTICIPANT_REQUIREMENT.insert(records)

}