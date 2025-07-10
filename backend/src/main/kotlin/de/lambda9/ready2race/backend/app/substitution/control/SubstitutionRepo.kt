package de.lambda9.ready2race.backend.app.substitution.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.SUBSTITUTION
import de.lambda9.ready2race.backend.database.generated.tables.references.SUBSTITUTION_VIEW
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.select
import java.util.UUID

object SubstitutionRepo {

    fun create(record: SubstitutionRecord) = SUBSTITUTION.insertReturning(record) { ID }

    fun get(id: UUID) = SUBSTITUTION_VIEW.select { ID.eq(id) }

    fun delete(id: UUID) = SUBSTITUTION.delete { ID.eq(id) }

    fun getByRound(setupRoundId: UUID) = SUBSTITUTION.select { COMPETITION_SETUP_ROUND.eq(setupRoundId) }

    fun getViewByRound(setupRoundId: UUID) = SUBSTITUTION_VIEW.select { COMPETITION_SETUP_ROUND_ID.eq(setupRoundId) }
}