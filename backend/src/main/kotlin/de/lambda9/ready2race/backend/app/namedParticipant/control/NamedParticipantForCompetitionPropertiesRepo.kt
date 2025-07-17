package de.lambda9.ready2race.backend.app.namedParticipant.control

import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.select
import java.util.*

object NamedParticipantForCompetitionPropertiesRepo {

    fun getByCompetition(competitionId: UUID) = NAMED_PARTICIPANT_FOR_COMPETITION_PROPERTIES.select { COMPETITION_ID.eq(competitionId) }

}