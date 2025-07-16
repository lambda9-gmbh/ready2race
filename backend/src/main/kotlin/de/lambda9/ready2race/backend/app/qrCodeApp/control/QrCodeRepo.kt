package de.lambda9.ready2race.backend.app.qrCodeApp.control

import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeDto
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.QrCodesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_ROLES
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_VIEW
import de.lambda9.ready2race.backend.database.generated.tables.references.QR_CODES
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object QrCodeRepo {

    fun update(qrCodeId: String, f: QrCodesRecord.() -> Unit) = QR_CODES.update(f) { QR_CODE_ID.eq(qrCodeId) }
    fun delete(qrCodeId: String) = QR_CODES.delete { QR_CODE_ID.eq(qrCodeId) }
    fun create(record: QrCodesRecord) = QR_CODES.insertReturning(record) { ID }
    
    fun findByCode(qrCodeId: String): JIO<QrCodesRecord?> = Jooq.query {
        selectFrom(QR_CODES)
            .where(QR_CODES.QR_CODE_ID.eq(qrCodeId))
            .fetchOne()
    }

    fun getUserOrParticipantByQrCodeId(
        qrCodeId: String
    ): JIO<QrCodeDto?> = Jooq.query {
        select()
            .from(QR_CODES)
            .leftJoin(APP_USER_WITH_ROLES).on(APP_USER_WITH_ROLES.ID.eq(QR_CODES.APP_USER))
            .leftJoin(PARTICIPANT_VIEW).on(PARTICIPANT_VIEW.ID.eq(QR_CODES.PARTICIPANT))
            .where(QR_CODES.QR_CODE_ID.eq(qrCodeId))
            .fetchOne {
                if (it[QR_CODES.APP_USER] != null) {
                    it.into(APP_USER_WITH_ROLES).toQrCodeAppuser(it[QR_CODES.QR_CODE_ID]!!)
                } else {
                    it.into(PARTICIPANT_VIEW).toQrCodeDto(it[QR_CODES.QR_CODE_ID]!!)
                }
            }
    }

}