package de.lambda9.ready2race.backend.app.competitionRegistration.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationSort
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationTeamDto
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*

object CompetitionRegistrationService {

    fun getByCompetition(
        params: PaginationParameters<CompetitionRegistrationSort>,
        competitionId: UUID,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
    ): App<ServiceError, ApiResponse.Page<CompetitionRegistrationTeamDto, CompetitionRegistrationSort>> =
        KIO.comprehension {

            // TODO add search?
            val total = !CompetitionRegistrationRepo.countForCompetition(competitionId, scope, user).orDie()
            val page = !CompetitionRegistrationRepo.pageForCompetition(competitionId, params, scope, user).orDie()

            KIO.ok(
                ApiResponse.Page(
                    data = page,
                    pagination = params.toPagination(total)
                )
            )
        }


}