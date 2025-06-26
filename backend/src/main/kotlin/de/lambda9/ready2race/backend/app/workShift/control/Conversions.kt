package de.lambda9.ready2race.backend.app.workShift.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.toNameDto
import de.lambda9.ready2race.backend.app.workShift.entity.WorkShiftUpsertDto
import de.lambda9.ready2race.backend.app.workShift.entity.WorkShiftWithAssignedUsersDto
import de.lambda9.ready2race.backend.database.generated.tables.records.WorkShiftRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WorkShiftWithAssignedUsersRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

fun WorkShiftUpsertDto.toRecord(eventId: UUID, userId: UUID): App<Nothing, WorkShiftRecord> = KIO.ok(
    LocalDateTime.now().let { now ->
        WorkShiftRecord(
            id = UUID.randomUUID(),
            event = eventId,
            timeFrom = timeFrom,
            timeTo = timeTo,
            workType = workType,
            remark = remark,
            minUser = minUser,
            maxUser = maxUser,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
    }
)

fun WorkShiftWithAssignedUsersRecord.toDto(): App<Nothing, WorkShiftWithAssignedUsersDto> = KIO.comprehension {
    val users = !assignedUser!!.toList().traverse { it!!.toNameDto() }

    KIO.ok(
        WorkShiftWithAssignedUsersDto(
            id = id!!,
            title = title!!,
            event = event!!,
            eventName = eventName!!,
            workTypeName = workTypeName!!,
            timeFrom = timeFrom!!,
            timeTo = timeTo!!,
            remark = remark,
            workType = workType!!,
            minUser = minUser!!,
            maxUser = maxUser,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
            assignedUsers = users,
        )
    )
}
