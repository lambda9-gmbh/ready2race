package de.lambda9.ready2race.testing

import de.lambda9.ready2race.backend.app.appuser.boundary.AppUserService
import de.lambda9.ready2race.backend.calls.pagination.Pagination
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.tailwind.core.KIO
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestExamples {

    @Test
    fun integrationTestExample() = testApplicationKIO {
        val foo = !AppUserService.page(PaginationParameters(null, null, null, null))
        assertTrue(foo.data.isEmpty())

        assertKIOSucceeds(
            expected = ApiResponse.Page(
                data = emptyList(),
                pagination = Pagination(0, null, null, null, null)
            )
        ) {
            AppUserService.page(PaginationParameters(null, null, null, null))
        }

        assertKIOSucceeds { KIO.ok(4) }
        assertKIOSucceeds(4) { KIO.ok(4) }

        assertKIODies { KIO.effectTotal { throw Exception() } }

        val response = client.get("/api/login")
        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}