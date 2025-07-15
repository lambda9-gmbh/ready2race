package de.lambda9.ready2race.backend.app.substitution.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.SUBSTITUTION
import de.lambda9.ready2race.backend.database.generated.tables.references.SUBSTITUTION_VIEW
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.select
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Field
import org.jooq.impl.DSL
import java.util.UUID

object SubstitutionRepo {

    fun create(record: SubstitutionRecord) = SUBSTITUTION.insertReturning(record) { ID }

    fun insert(records: List<SubstitutionRecord>) = SUBSTITUTION.insert(records)

    fun get(id: UUID) = SUBSTITUTION_VIEW.select { ID.eq(id) }

    fun delete(ids: List<UUID>) = SUBSTITUTION.delete { ID.`in`(ids) }

    fun getViewByRound(setupRoundId: UUID) = SUBSTITUTION_VIEW.select { COMPETITION_SETUP_ROUND_ID.eq(setupRoundId) }

    fun copySubstitutionsToNewRound(oldSetupRound: UUID, newSetupRound: UUID): JIO<Int> = Jooq.query {
        with(SUBSTITUTION) {

            val fieldsToCopy = fields().filter { it.name != ID.name && it.name != COMPETITION_SETUP_ROUND.name }

            insertInto(this)
                .columns(fieldsToCopy + ID + COMPETITION_SETUP_ROUND)
                .select(
                    DSL.select(
                        fieldsToCopy
                            + DSL.field("gen_random_uuid()", UUID::class.java).`as`(ID)
                            + DSL.`val`(newSetupRound).`as`(COMPETITION_SETUP_ROUND)
                    )
                        .from(this)
                        .where(COMPETITION_SETUP_ROUND.eq(oldSetupRound))
                )
                .execute()
        }
    }
}