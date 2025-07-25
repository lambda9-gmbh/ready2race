package de.lambda9.ready2race.backend.app.substitution.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.SUBSTITUTION
import de.lambda9.ready2race.backend.database.generated.tables.references.SUBSTITUTION_VIEW
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.select
import java.util.UUID

object SubstitutionRepo {

    fun create(record: SubstitutionRecord) = SUBSTITUTION.insertReturning(record) { ID }

    fun insert(records: List<SubstitutionRecord>) = SUBSTITUTION.insert(records)

    fun getByRound(setupRoundId: UUID) = SUBSTITUTION.select { COMPETITION_SETUP_ROUND.eq(setupRoundId) }

    fun delete(ids: List<UUID>) = SUBSTITUTION.delete { ID.`in`(ids) }

    fun deleteBySetupRoundId(setupRoundId: UUID) = SUBSTITUTION.delete { COMPETITION_SETUP_ROUND.eq(setupRoundId) }

    fun getViewByRound(setupRoundId: UUID) = SUBSTITUTION_VIEW.select { COMPETITION_SETUP_ROUND_ID.eq(setupRoundId) }
}