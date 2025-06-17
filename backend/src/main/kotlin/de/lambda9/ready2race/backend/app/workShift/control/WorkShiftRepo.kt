package de.lambda9.ready2race.backend.app.workShift.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.WorkShiftRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WORK_SHIFT
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.update
import java.util.*

object WorkShiftRepo {

    fun create(record: WorkShiftRecord) = WORK_SHIFT.insertReturning(record) { ID }

    fun update(id: UUID, f: WorkShiftRecord.() -> Unit) =
        WORK_SHIFT.update(f) { ID.eq(id) }

    fun delete(id: UUID) = WORK_SHIFT.delete { ID.eq(id) }

}