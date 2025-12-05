package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.app.appuser.entity.PendingClubRepresentativeApprovalDto
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserClubRepresentativeApprovalRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_CLUB_REPRESENTATIVE_APPROVAL
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object AppUserClubRepresentativeApprovalRepo {

    fun create(record: AppUserClubRepresentativeApprovalRecord) = APP_USER_CLUB_REPRESENTATIVE_APPROVAL.insert(record)

    fun getPendingByClubId(clubId: UUID): JIO<List<PendingClubRepresentativeApprovalDto>> = Jooq.query {
        select(
            APP_USER_CLUB_REPRESENTATIVE_APPROVAL.APP_USER,
            APP_USER.FIRSTNAME,
            APP_USER.LASTNAME,
            APP_USER.EMAIL,
            APP_USER_CLUB_REPRESENTATIVE_APPROVAL.CREATED_AT
        )
            .from(APP_USER_CLUB_REPRESENTATIVE_APPROVAL)
            .join(APP_USER).on(APP_USER.ID.eq(APP_USER_CLUB_REPRESENTATIVE_APPROVAL.APP_USER))
            .where(APP_USER_CLUB_REPRESENTATIVE_APPROVAL.CLUB.eq(clubId))
            .and(APP_USER_CLUB_REPRESENTATIVE_APPROVAL.APPROVED.eq(false))
            .fetch {
                PendingClubRepresentativeApprovalDto(
                    userId = it[APP_USER_CLUB_REPRESENTATIVE_APPROVAL.APP_USER]!!,
                    firstName = it[APP_USER.FIRSTNAME]!!,
                    lastName = it[APP_USER.LASTNAME]!!,
                    email = it[APP_USER.EMAIL]!!,
                    createdAt = it[APP_USER_CLUB_REPRESENTATIVE_APPROVAL.CREATED_AT]!!
                )
            }
    }

    fun update(appUserId: UUID, f: AppUserClubRepresentativeApprovalRecord.() -> Unit) =
        APP_USER_CLUB_REPRESENTATIVE_APPROVAL.update(f) { APP_USER.eq(appUserId) }
}