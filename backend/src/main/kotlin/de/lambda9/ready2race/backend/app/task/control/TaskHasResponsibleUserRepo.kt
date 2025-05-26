package de.lambda9.ready2race.backend.app.task.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.TaskHasResponsibleUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.TASK_HAS_RESPONSIBLE_USER
import de.lambda9.ready2race.backend.database.insert
import java.util.*

object TaskHasResponsibleUserRepo {

    fun create(records: Collection<TaskHasResponsibleUserRecord>) = TASK_HAS_RESPONSIBLE_USER.insert(records)

    fun deleteAll(taskId: UUID) = TASK_HAS_RESPONSIBLE_USER.delete { TASK.eq(taskId) }

}