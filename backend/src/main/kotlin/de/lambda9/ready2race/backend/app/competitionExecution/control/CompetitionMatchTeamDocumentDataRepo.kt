package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamDocumentDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM_DOCUMENT_DATA
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM_DOCUMENT_DOWNLOAD
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.selectOne
import java.util.UUID

object CompetitionMatchTeamDocumentDataRepo {

    fun create(record: CompetitionMatchTeamDocumentDataRecord) = COMPETITION_MATCH_TEAM_DOCUMENT_DATA.insert(record)

    fun getDownload(id: UUID) = COMPETITION_MATCH_TEAM_DOCUMENT_DOWNLOAD.selectOne { ID.eq(id) }
}