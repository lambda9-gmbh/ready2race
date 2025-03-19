package de.lambda9.ready2race.testing

import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.control.EmailRepo
import de.lambda9.ready2race.backend.app.email.entity.EmailBody
import de.lambda9.ready2race.backend.app.email.entity.EmailContent
import de.lambda9.tailwind.core.KIO
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.*
import kotlin.time.Duration

/* TODO: @fix: When trying to check, if sandbox is working, it is not working always.
 * TODO: Strangely, it does not work in combination with some client calls.
 * TODO: Just run these tests to see.
 */


class TestExamples {

    @Test
    fun comprehensionTestExample() = testComprehension {
        val email1 = !EmailRepo.getAndLockNext(Duration.ZERO)
        assertNull(email1)

        !EmailService.enqueue(
            recipient = "recipient",
            content = EmailContent(subject = "Hello", body = EmailBody.Text("World!"))
        )

        val email2 = !EmailRepo.getAndLockNext(Duration.ZERO)
        assertNotNull(email2)
    }

    @Test
    fun comprehensionTestExample1() = testComprehension {
        val email1 = !EmailRepo.getAndLockNext(Duration.ZERO)
        assertNull(email1)

        !EmailService.enqueue(
            recipient = "recipient1",
            content = EmailContent(subject = "Hello", body = EmailBody.Text("World!"))
        )

        val email2 = !EmailRepo.getAndLockNext(Duration.ZERO)
        assertNotNull(email2)
    }

    @Test
    fun integrationTestExample4() = testApplicationComprehension { // gets rolled-back -> 4

        val email1 = !EmailRepo.getAndLockNext(Duration.ZERO)
        println(email1?.recipient)

        email1?.delete()

        val email2 = !EmailRepo.getAndLockNext(Duration.ZERO)
        println(email2?.recipient)

        email2?.delete()

        val email3 = !EmailRepo.getAndLockNext(Duration.ZERO)
        println(email3?.recipient)

        email3?.delete()

    }

    @Test
    fun integrationTestExample3() = testApplicationComprehension { // gets rolled-back -> 4

        val email1 = !EmailRepo.getAndLockNext(Duration.ZERO)
        println(email1?.recipient)

        email1?.delete()

        val email2 = !EmailRepo.getAndLockNext(Duration.ZERO)
        println(email2?.recipient)

        email2?.delete()

        val email3 = !EmailRepo.getAndLockNext(Duration.ZERO)
        println(email3?.recipient)

        email3?.delete()

    }
    @Test
    fun integrationTestExample2() = testApplicationComprehension {

        !EmailService.enqueue(
            recipient = "recipient2",
            content = EmailContent(subject = "Hello", body = EmailBody.Text("World!"))
        )

        // this hinders rollback
        // it doesn't even connect to the DB
        val response = client.get("/api/login")
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun integrationTestExample1() = testApplicationComprehension {

        !EmailService.enqueue(
            recipient = "recipient1",
            content = EmailContent(subject = "Hello", body = EmailBody.Text("World!"))
        )

        // this does not hinder rollback
        // this also doesn't connect the DB
        val response = client.post("/api/login")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun integrationTestExample0() = testApplicationComprehension {

        !EmailService.enqueue(
            recipient = "recipient0",
            content = EmailContent(subject = "Hello", body = EmailBody.Text("World!"))
        )

        // this hinders rollback
        // this does connect the DB
        val response = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}