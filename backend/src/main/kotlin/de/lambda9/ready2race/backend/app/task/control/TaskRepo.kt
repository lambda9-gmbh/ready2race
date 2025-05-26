package de.lambda9.ready2race.backend.app.task.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.TaskRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.TASK
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.update
import java.util.*

object TaskRepo {

    fun create(record: TaskRecord) = TASK.insertReturning(record) { ID }

    fun update(id: UUID, eventId: UUID, f: TaskRecord.() -> Unit) = TASK.update(f) { ID.eq(id).and(EVENT.eq(eventId)) }

    fun delete(id: UUID, eventId: UUID) = TASK.delete { ID.eq(id).and(EVENT.eq(eventId)) }

}