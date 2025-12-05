package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserClubRepresentativeApprovalRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_CLUB_REPRESENTATIVE_APPROVAL
import de.lambda9.ready2race.backend.database.insert

object AppUserClubRepresentativeApprovalRepo {

    fun create(record: AppUserClubRepresentativeApprovalRecord) = APP_USER_CLUB_REPRESENTATIVE_APPROVAL.insert(record)
}