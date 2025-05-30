package de.lambda9.ready2race.testing

import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.control.EmailRepo
import de.lambda9.ready2race.backend.app.email.entity.EmailBody
import de.lambda9.ready2race.backend.app.email.entity.EmailContent
import de.lambda9.ready2race.backend.app.email.entity.EmailError
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_SESSION
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.jooq.Jooq
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.*
import kotlin.time.Duration

class UnitTestExamples {

    @Test
    fun comprehensionTestExample() = testComprehension {
        assertKIOSucceeds(null) {
            EmailRepo.getAndLockNext(Duration.ZERO)
        }

        !EmailService.enqueue(
            recipient = "recipient",
            content = EmailContent(subject = "Hello", body = EmailBody.Text("World!"))
        )

        val email2 = !EmailRepo.getAndLockNext(Duration.ZERO)
        assertNotNull(email2)
    }

    @Test
    fun comprehensionTestExample1() = testComprehension {

        assertKIOFails(EmailError.NoEmailsToSend) {
            EmailRepo.getAndLockNext(Duration.ZERO).onNullFail { EmailError.NoEmailsToSend }
        }

        !EmailService.enqueue(
            recipient = "recipient1",
            content = EmailContent(subject = "Hello", body = EmailBody.Text("World!"))
        )

        assertKIOSucceeds {
            EmailRepo.getAndLockNext(Duration.ZERO).onNullFail { EmailError.NoEmailsToSend }
        }
    }
}

class IntegrationTestExamplesIT {

    @Test
    fun integrationTestExample0() = testApplicationComprehension {

        val sessionExistingBefore = !Jooq.query {
            fetchExists(APP_USER_SESSION)
        }
        assertFalse(sessionExistingBefore)

        val response = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val sessionExistingAfter = !Jooq.query {
            fetchExists(APP_USER_SESSION)
        }
        assertTrue(sessionExistingAfter)
    }

    @Test
    fun integrationTestExample1() = testApplicationComprehension {

        assertKIOSucceeds(false) {
            Jooq.query {
                fetchExists(APP_USER_SESSION)
            }
        }

        val response = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        assertKIOSucceeds(true) {
            Jooq.query {
                fetchExists(APP_USER_SESSION)
            }
        }
    }
}