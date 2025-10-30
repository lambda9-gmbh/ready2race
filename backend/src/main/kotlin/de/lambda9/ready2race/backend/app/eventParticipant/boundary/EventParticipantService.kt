package de.lambda9.ready2race.backend.app.eventParticipant.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplatePlaceholder
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventParticipant.control.EventParticipantRepo
import de.lambda9.ready2race.backend.app.eventParticipant.entity.EventParticipantError
import de.lambda9.ready2race.backend.app.eventParticipant.entity.ResendAccessTokenRequest
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.security.RandomUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.UUID

object EventParticipantService {

    fun resendAccessToken(
        request: ResendAccessTokenRequest,
        eventId: UUID,
        participantId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val participant = !ParticipantRepo.get(participantId).orDie().onNullFail { ParticipantError.ParticipantNotFound }.failIf({
            it.email == null
        }) { EventParticipantError.NoEmail }

        !KIO.failOn(scope == Privilege.Scope.OWN && user.club != participant.club) { AuthError.PrivilegeMissing }

        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }.failIf({
            it.challengeEvent != true || it.selfSubmission != true
        }) { EventParticipantError.NoChallengeWithSelfSubmission }

        val newToken = RandomUtilities.token()

        !EventParticipantRepo.updateAccessToken(eventId, participantId, newToken).orDie()

        val content = !EmailService.getTemplate(
            EmailTemplateKey.PARTICIPANT_CHALLENGE_REGISTERED,
            EmailLanguage.DE, // TODO: somehow get a language
        ).map { template ->
            template.toContent(
                EmailTemplatePlaceholder.RECIPIENT to participant.firstname + " " + participant.lastname,
                EmailTemplatePlaceholder.EVENT to event.name,
                EmailTemplatePlaceholder.LINK to request.callbackUrl + newToken,
            )
        }

        !EmailService.enqueue(
            recipient = participant.email!!,
            content = content,
        )

        noData
    }

}