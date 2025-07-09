package de.lambda9.ready2race.backend.app.substitution.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.SUBSTITUTION
import de.lambda9.ready2race.backend.database.generated.tables.references.SUBSTITUTION_VIEW
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object SubstitutionRepo {

    fun create(record: SubstitutionRecord) = SUBSTITUTION.insertReturning(record) { ID }

    fun get(
        id: UUID,
    ): JIO<SubstitutionViewRecord?> = Jooq.query {
        with(SUBSTITUTION_VIEW) {
            selectFrom(this)
                .where(SUBSTITUTION_VIEW.ID.eq(id))
                .fetchOne()
        }
    }

    fun update(id: UUID, f: SubstitutionRecord.() -> Unit) = SUBSTITUTION.update(f) { ID.eq(id) }


    fun delete(id: UUID) = SUBSTITUTION.delete { ID.eq(id) }
}