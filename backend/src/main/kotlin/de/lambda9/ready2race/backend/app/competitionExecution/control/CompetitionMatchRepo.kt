package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_WITH_TEAMS
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.STARTLIST_VIEW
import java.util.*

object CompetitionMatchRepo {
    fun create(records: List<CompetitionMatchRecord>) = COMPETITION_MATCH.insert(records)

    fun exists(id: UUID) = COMPETITION_MATCH.exists { COMPETITION_SETUP_MATCH.eq(id) }

    fun update(id: UUID, f: CompetitionMatchRecord.() -> Unit) =
        COMPETITION_MATCH.update(f) { COMPETITION_SETUP_MATCH.eq(id) }

    fun delete(ids: List<UUID>) = COMPETITION_MATCH.delete { COMPETITION_SETUP_MATCH.`in`(ids) }

    fun getForStartList(id: UUID) = STARTLIST_VIEW.selectOne { ID.eq(id) }
}