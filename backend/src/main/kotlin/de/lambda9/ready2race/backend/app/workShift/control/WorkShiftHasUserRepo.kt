package de.lambda9.ready2race.backend.app.workShift.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.WorkShiftHasUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WORK_SHIFT_HAS_USER
import de.lambda9.ready2race.backend.database.insert
import java.util.*

object WorkShiftHasUserRepo {

    fun create(records: Collection<WorkShiftHasUserRecord>) = WORK_SHIFT_HAS_USER.insert(records)

    fun deleteAll(workShiftId: UUID) = WORK_SHIFT_HAS_USER.delete { WORK_SHIFT.eq(workShiftId) }

}