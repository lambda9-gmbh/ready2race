package de.lambda9.ready2race.backend.app.appUserWithQrCode.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appUserWithQrCode.entity.AppUserWithQrCodeDto
import de.lambda9.ready2race.backend.app.role.control.toDto
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithQrCodeForEventRecord
import de.lambda9.tailwind.core.extensions.kio.traverse

fun AppUserWithQrCodeForEventRecord.toAppUserWithQrCodeDto(): App<Nothing, AppUserWithQrCodeDto> =
    roles!!.toList().traverse { it!!.toDto() }.map {
        AppUserWithQrCodeDto(
            id = id!!,
            firstname = firstname!!,
            lastname = lastname!!,
            email = email!!,
            club = club,
            roles = it,
            qrCodeId = qrCodeId!!,
            eventId = eventId!!,
            createdAt = createdAt!!,
            createdBy = createdBy
        )
    }