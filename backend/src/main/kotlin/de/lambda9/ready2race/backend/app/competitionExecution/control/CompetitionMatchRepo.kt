package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.update
import java.util.*

object CompetitionMatchRepo {
    fun create(records: List<CompetitionMatchRecord>) = COMPETITION_MATCH.insert(records)

    fun update(id: UUID, f: CompetitionMatchRecord.() -> Unit) =
        COMPETITION_MATCH.update(f) { COMPETITION_SETUP_MATCH.eq(id) }
}