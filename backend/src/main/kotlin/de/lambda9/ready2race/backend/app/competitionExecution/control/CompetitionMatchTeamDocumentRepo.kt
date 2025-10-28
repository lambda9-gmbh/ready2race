package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamDocumentRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM_DOCUMENT
import de.lambda9.ready2race.backend.database.insertReturning

object CompetitionMatchTeamDocumentRepo {

    fun create(record: CompetitionMatchTeamDocumentRecord) =
        COMPETITION_MATCH_TEAM_DOCUMENT.insertReturning(record) { ID }

}