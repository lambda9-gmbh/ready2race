package de.lambda9.ready2race.backend.app.appuser

import de.lambda9.ready2race.backend.app.appuser.boundary.AppUserService
import de.lambda9.ready2race.backend.app.appuser.control.AppUserInvitationHasRoleRepo
import de.lambda9.ready2race.backend.app.appuser.control.AppUserInvitationRepo
import de.lambda9.ready2race.backend.app.appuser.entity.AcceptInvitationRequest
import de.lambda9.ready2race.backend.app.appuser.entity.AppUserError
import de.lambda9.ready2race.backend.app.appuser.entity.ResendInvitationRequest
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.database.ADMIN_ROLE
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationHasRoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION_HAS_ROLE
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION_TO_EMAIL
import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL
import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL_ADDRESS
import de.lambda9.ready2race.backend.security.RandomUtilities
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Zurueckziehen und erneutes Verschicken einer Einladung gegen ein echtes Postgres.
 *
 * Beide Wege haengen an Datenbankverhalten, das sich in reinen Kotlin-Tests nicht zeigt: das
 * Zuruecknehmen verlaesst sich darauf, dass Fremdschluessel und die Regel
 * `delete_email_app_user_invitation` hinter der geloeschten Zeile aufraeumen, und das erneute
 * Verschicken laeuft in den eindeutigen Index auf `app_user_invitation_to_email`, sobald man es
 * zweimal tut.
 */
class InvitationWithdrawAndResendTest {

    private val email = "eingeladen@example.com"

    private val sender = AppUserWithPrivilegesRecord(
        id = UUID.randomUUID(),
        firstname = "Ilka",
        lastname = "Regattaleitung",
    )

    private fun invitation(
        token: String = RandomUtilities.token(),
        expiresAt: LocalDateTime = LocalDateTime.now().plusDays(7),
    ) = AppUserInvitationRecord(
        id = UUID.randomUUID(),
        token = token,
        email = email,
        firstname = "Neue",
        lastname = "Person",
        language = EmailLanguage.DE.name,
        expiresAt = expiresAt,
        createdAt = LocalDateTime.now(),
        createdBy = null,
    )

    private fun resendRequest() = ResendInvitationRequest(callbackUrl = "https://example.com/invitation/")

    @Test
    fun withdrawingAnInvitationFreesTheAddressForANewInvite() = testComprehension {
        val id = !AppUserInvitationRepo.create(invitation())
        !AppUserInvitationHasRoleRepo.create(
            listOf(AppUserInvitationHasRoleRecord(appUserInvitation = id, role = ADMIN_ROLE))
        )

        // Die Regel auf app_user_invitation belegt die Adresse - genau das laesst ein zweites
        // Einladen derselben Person sonst in EmailAlreadyInUse laufen.
        assertTrue(!Jooq.query { fetchExists(EMAIL_ADDRESS, EMAIL_ADDRESS.EMAIL.eq(email)) })

        !AppUserService.deleteInvitation(id)

        assertEquals(0, !Jooq.query { fetchCount(APP_USER_INVITATION, APP_USER_INVITATION.ID.eq(id)) })
        assertEquals(
            0,
            !Jooq.query {
                fetchCount(APP_USER_INVITATION_HAS_ROLE, APP_USER_INVITATION_HAS_ROLE.APP_USER_INVITATION.eq(id))
            },
        )
        assertFalse(
            !Jooq.query { fetchExists(EMAIL_ADDRESS, EMAIL_ADDRESS.EMAIL.eq(email)) },
            "Die Adresse muss wieder frei sein, sonst kann man die Person nicht erneut einladen",
        )
    }

    @Test
    fun withdrawingAnUnknownInvitationFails() = testComprehension {
        assertKIOFails(AppUserError.InvitationNotFound) {
            AppUserService.deleteInvitation(UUID.randomUUID())
        }
    }

    @Test
    fun resendingTwiceKeepsASingleEmailLinkPointingAtTheLatestMail() = testComprehension {
        val id = !AppUserInvitationRepo.create(invitation())

        !AppUserService.resendInvitation(resendRequest(), id, sender)
        val firstMail = !linkedMail(id)
        assertNotNull(firstMail)

        // Der zweite Durchgang ist der eigentliche Pruefpunkt: auf app_user_invitation_to_email
        // liegt ein eindeutiger Index, ein zweiter Insert wuerde hier sterben.
        !AppUserService.resendInvitation(resendRequest(), id, sender)
        val secondMail = !linkedMail(id)

        assertEquals(
            1,
            !Jooq.query {
                fetchCount(APP_USER_INVITATION_TO_EMAIL, APP_USER_INVITATION_TO_EMAIL.APP_USER_INVITATION.eq(id))
            },
            "Die Verknuepfung zeigt auf genau eine Mail",
        )
        assertNotEquals(firstMail, secondMail, "und zwar auf die zuletzt verschickte")
        assertEquals(2, !Jooq.query { fetchCount(EMAIL, EMAIL.RECIPIENT.eq(email)) })
    }

    @Test
    fun resendingIssuesANewTokenAndRefreshesTheDeadline() = testComprehension {
        val oldToken = RandomUtilities.token()
        val id = !AppUserInvitationRepo.create(
            invitation(token = oldToken, expiresAt = LocalDateTime.now().plusHours(2))
        )

        !AppUserService.resendInvitation(resendRequest(), id, sender)

        val refreshed = !Jooq.query { fetchOne(APP_USER_INVITATION, APP_USER_INVITATION.ID.eq(id)) }
        assertNotNull(refreshed)
        assertNotEquals(oldToken, refreshed.token)
        assertTrue(
            refreshed.expiresAt.isAfter(LocalDateTime.now().plusDays(6)),
            "Das erneute Verschicken gibt der Einladung wieder volle sieben Tage",
        )

        // Der Link aus der ersten Mail ist damit tot.
        assertNull(!Jooq.query { fetchOne(APP_USER_INVITATION, APP_USER_INVITATION.TOKEN.eq(oldToken)) })
        assertKIOFails(AppUserError.InvitationNotFound) {
            AppUserService.acceptInvitation(AcceptInvitationRequest(token = oldToken, password = "5kFlg09?\$!dF"))
        }
    }

    @Test
    fun resendingAnUnknownInvitationFails() = testComprehension {
        assertKIOFails(AppUserError.InvitationNotFound) {
            AppUserService.resendInvitation(resendRequest(), UUID.randomUUID(), sender)
        }
    }

    private fun linkedMail(invitationId: UUID) = Jooq.query {
        fetchOne(APP_USER_INVITATION_TO_EMAIL, APP_USER_INVITATION_TO_EMAIL.APP_USER_INVITATION.eq(invitationId))
            ?.email
    }
}
