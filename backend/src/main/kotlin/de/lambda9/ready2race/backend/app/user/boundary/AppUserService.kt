package de.lambda9.ready2race.backend.app.user.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.user.control.AppUserRepo
import de.lambda9.ready2race.backend.app.user.control.appUserDto
import de.lambda9.ready2race.backend.app.user.entity.AppUserDto
import de.lambda9.ready2race.backend.app.user.entity.AppUserWithRolesSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.orDie

object AppUserService {

    fun page(
        params: PaginationParameters<AppUserWithRolesSort>,
    ): App<Nothing, ApiResponse.Page<AppUserDto, AppUserWithRolesSort>> = KIO.comprehension {
        val total = !AppUserRepo.countWithRoles(params.search).orDie()
        val page = !AppUserRepo.pageWithRoles(params).orDie()

        page.forEachM { it.appUserDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }
}