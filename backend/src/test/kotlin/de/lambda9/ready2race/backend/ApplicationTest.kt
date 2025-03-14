package de.lambda9.ready2race.backend

import de.lambda9.ready2race.backend.app.appuser.boundary.AppUserService
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.testing.testApplicationKIO
import kotlin.test.Test
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun foo() = testApplicationKIO {
        val foo = !AppUserService.page(PaginationParameters(null, null, null, null))
        assertTrue(foo.data.isEmpty())
    }

}