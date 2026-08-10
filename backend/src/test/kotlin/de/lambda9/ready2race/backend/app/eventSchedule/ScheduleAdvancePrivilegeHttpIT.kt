package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserHasRoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventScheduleSlotRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleHasPrivilegeRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_HAS_ROLE
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_SCHEDULE_SLOT
import de.lambda9.ready2race.backend.database.generated.tables.references.PRIVILEGE
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE_HAS_PRIVILEGE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.ready2race.testing.testApplicationComprehension
import de.lambda9.tailwind.jooq.Jooq
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.ClientProvider
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Die Berechtigungsgrenze des Vorziehens auf der Leitung.
 *
 * Das ist keine Formalie, sondern die eine Entscheidung, die dieser Endpunkt gegenüber dem Absagen
 * anders trifft: `PUT /slot/{id}/skip` lässt UPDATE EVENT **oder** UPDATE LIVE_DASHBOARD zu, damit
 * der Schiedsrichter einen Lauf absagen kann. Das Vorziehen baut dagegen den Zeitplan um und bleibt
 * dem Regattabüro vorbehalten - genau wie das Verschieben-Werkzeug. Ein Schiedsrichter, der beides
 * dürfte, könnte vom Steg aus die gedruckten Startzeiten eines halben Renntags verschieben.
 *
 * Geprüft wird die Grenze am Vorziehen selbst, samt Gegenbeleg aus dem Büro; dass der
 * Schiedsrichter einen LAUF absagen darf, deckt der Zeitplan-Test des Absagens ab (ein
 * Programmpunkt wie hier bleibt ihm ohnehin verwehrt, siehe FreeSlotSkipReservedForOffice).
 *
 * Laeuft als `IT` nicht in der normalen Suite mit (kein Failsafe im POM, Surefire nimmt nur
 * `*Test`), weil `testApplicationComprehension` die Datenbank zurücksetzt. Gezielt starten:
 *
 * ```
 * ./mvnw -o test -Dtest=ScheduleAdvancePrivilegeHttpIT -DfailIfNoSpecifiedTests=false
 * ```
 */
class ScheduleAdvancePrivilegeHttpIT {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 9, 12, 0)
    private val refereePassword = "schiedsrichter"

    /** Die Sitzung reist im Header, nicht im Cookie (Sessions.kt: header<UserSession>). */
    private suspend fun ClientProvider.login(email: String, password: String): String {
        val response = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return assertNotNull(response.headers["X-Api-Session"], "Login muss eine Sitzung ausgeben")
    }

    @Test
    fun onlyTheEventOfficeMayMoveTheScheduleUp() = testApplicationComprehension {
        val eventId = UUID.randomUUID()
        val cancelledSlot = UUID.randomUUID()
        val followingSlot = UUID.randomUUID()
        val refereeEmail = "schiedsrichter@example.org"

        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))
        !EVENT_SCHEDULE_SLOT.insert(
            EventScheduleSlotRecord(
                id = cancelledSlot,
                event = eventId,
                startTime = LocalDateTime.of(2026, 8, 17, 10, 0),
                name = "Lauf A",
                durationMinutes = 20,
                createdAt = now,
                updatedAt = now,
            )
        )
        !EVENT_SCHEDULE_SLOT.insert(
            EventScheduleSlotRecord(
                id = followingSlot,
                event = eventId,
                startTime = LocalDateTime.of(2026, 8, 17, 10, 20),
                name = "Lauf B",
                createdAt = now,
                updatedAt = now,
            )
        )

        // Ein Konto mit genau einem Recht: UPDATE LIVE_DASHBOARD, das Recht des
        // Schiedsrichter-Dashboards. Kein UPDATE EVENT.
        val refereeId = UUID.randomUUID()
        val refereeRole = UUID.randomUUID()
        !APP_USER.insert(
            AppUserRecord(
                id = refereeId,
                email = refereeEmail,
                firstname = "Sina",
                lastname = "Schiedsrichter",
                password = !PasswordUtilities.hash(refereePassword),
                language = "DE",
                createdAt = now,
                updatedAt = now,
            )
        )
        !ROLE.insert(
            RoleRecord(
                id = refereeRole,
                name = "Schiedsrichter",
                static = false,
                createdAt = now,
                updatedAt = now,
            )
        )
        !APP_USER_HAS_ROLE.insert(AppUserHasRoleRecord(appUser = refereeId, role = refereeRole))

        val liveDashboardPrivilege = Privilege.UpdateLiveDashboardGlobal
        val privilegeId = assertNotNull(
            !Jooq.query {
                select(PRIVILEGE.ID)
                    .from(PRIVILEGE)
                    .where(PRIVILEGE.ACTION.eq(liveDashboardPrivilege.action.name))
                    .and(PRIVILEGE.RESOURCE.eq(liveDashboardPrivilege.resource.name))
                    .and(PRIVILEGE.SCOPE.eq(liveDashboardPrivilege.scope.name))
                    .fetchOne(PRIVILEGE.ID)
            },
            "initializeDatabase legt alle Privilegien an",
        )
        !ROLE_HAS_PRIVILEGE.insert(
            RoleHasPrivilegeRecord(role = refereeRole, privilege = privilegeId)
        )

        val refereeSession = login(refereeEmail, refereePassword)

        // Den Zeitplan umbauen darf der Schiedsrichter nicht - auch nicht als folgenlose Vorschau.
        val advanceAsReferee = client.post("/api/event/$eventId/schedule/slot/$cancelledSlot/advance") {
            header("X-Api-Session", refereeSession)
            contentType(ContentType.Application.Json)
            setBody("""{"targetSlotId":"$followingSlot","dryRun":true}""")
        }
        assertEquals(HttpStatusCode.Forbidden, advanceAsReferee.status, advanceAsReferee.bodyAsText())

        // Ohne Sitzung erst recht nicht.
        val anonymous = client.post("/api/event/$eventId/schedule/slot/$cancelledSlot/advance") {
            contentType(ContentType.Application.Json)
            setBody("""{"targetSlotId":"$followingSlot","dryRun":true}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status, anonymous.bodyAsText())

        // Der Gegenbeleg, ohne den die 403 nichts wert wäre: dieselbe Anfrage geht durch, sobald
        // sie aus dem Regattabüro kommt. Die Ablehnung oben liegt also am Recht und nicht daran,
        // dass an der Anfrage selbst etwas nicht stimmt.
        val officeSession = login("admin", "admin")

        val skip = client.put("/api/event/$eventId/schedule/slot/$cancelledSlot/skip") {
            header("X-Api-Session", officeSession)
        }
        assertEquals(HttpStatusCode.NoContent, skip.status, skip.bodyAsText())

        val advanceAsOffice = client.post("/api/event/$eventId/schedule/slot/$cancelledSlot/advance") {
            header("X-Api-Session", officeSession)
            contentType(ContentType.Application.Json)
            setBody("""{"targetSlotId":"$followingSlot","dryRun":true}""")
        }
        assertEquals(HttpStatusCode.OK, advanceAsOffice.status, advanceAsOffice.bodyAsText())
    }
}
