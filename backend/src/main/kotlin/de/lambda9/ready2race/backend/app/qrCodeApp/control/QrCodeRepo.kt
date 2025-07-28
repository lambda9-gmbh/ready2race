package de.lambda9.ready2race.backend.app.qrCodeApp.control

import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeDto
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.QrCodesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_ROLES
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_VIEW
import de.lambda9.ready2race.backend.database.generated.tables.references.QR_CODES
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_NAMED_PARTICIPANT
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

    fun getUserOrParticipantByQrCodeIdWithDetails(
        qrCodeId: String
    ): JIO<QrCodeDto?> = Jooq.query {
        val qrCode = selectFrom(QR_CODES)
            .where(QR_CODES.QR_CODE_ID.eq(qrCodeId))
            .fetchOne() ?: return@query null

        if (qrCode.appUser != null) {
            // AppUser with club name
            val appUserData = select(
                APP_USER_WITH_ROLES.asterisk(),
                CLUB.NAME
            )
                .from(APP_USER_WITH_ROLES)
                .leftJoin(APP_USER).on(APP_USER.ID.eq(APP_USER_WITH_ROLES.ID))
                .leftJoin(CLUB).on(CLUB.ID.eq(APP_USER.CLUB))
                .where(APP_USER_WITH_ROLES.ID.eq(qrCode.appUser))
                .fetchOne()

            appUserData?.let {
                it.into(APP_USER_WITH_ROLES).toQrCodeAppuserWithClub(
                    qrCodeId = qrCode.qrCodeId!!,
                    clubName = it[CLUB.NAME]
                )
            }
        } else if (qrCode.participant != null) {
            // Participant with club name and competitions
            val participantData = select(
                PARTICIPANT_VIEW.asterisk(),
                CLUB.NAME
            )
                .from(PARTICIPANT_VIEW)
                .leftJoin(PARTICIPANT).on(PARTICIPANT.ID.eq(PARTICIPANT_VIEW.ID))
                .leftJoin(CLUB).on(CLUB.ID.eq(PARTICIPANT.CLUB))
                .where(PARTICIPANT_VIEW.ID.eq(qrCode.participant))
                .fetchOne()

            // Get competitions for participant
            val competitions = selectDistinct(COMPETITION_PROPERTIES.NAME)
                .from(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
                .join(COMPETITION_REGISTRATION).on(COMPETITION_REGISTRATION.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION))
                .join(COMPETITION).on(COMPETITION.ID.eq(COMPETITION_REGISTRATION.COMPETITION))
                .join(COMPETITION_PROPERTIES).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
                .where(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT.eq(qrCode.participant))
                .fetch { it[COMPETITION_PROPERTIES.NAME] }
                .filterNotNull()

            participantData?.let {
                it.into(PARTICIPANT_VIEW).toQrCodeDtoWithDetails(
                    qrCodeId = qrCode.qrCodeId!!,
                    clubName = it[CLUB.NAME],
                    competitions = competitions
                )
            }
        } else {
            null
        }
    }

}