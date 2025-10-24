package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamDocumentDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM_DOCUMENT_DATA
import de.lambda9.ready2race.backend.database.insert

object CompetitionMatchTeamDocumentDataRepo {

    fun create(record: CompetitionMatchTeamDocumentDataRecord) = COMPETITION_MATCH_TEAM_DOCUMENT_DATA.insert(record)

}