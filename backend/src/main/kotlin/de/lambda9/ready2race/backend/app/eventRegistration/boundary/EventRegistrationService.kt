package de.lambda9.ready2race.backend.app.eventRegistration.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationOptionalFeeRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.entity.EmailAttachment
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplatePlaceholder
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventDocument.control.EventDocumentRepo
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationRepo
import de.lambda9.ready2race.backend.app.eventRegistration.control.toDto
import de.lambda9.ready2race.backend.app.eventRegistration.control.toRecord
import de.lambda9.ready2race.backend.app.eventRegistration.entity.*
import de.lambda9.ready2race.backend.app.participant.control.ParticipantForEventRepo
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_COMPETITION_REGISTRATION
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.ok
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.*

object EventRegistrationService {

    fun pageView(
        params: PaginationParameters<EventRegistrationViewSort>,
    ): App<Nothing, ApiResponse.Page<EventRegistrationViewDto, EventRegistrationViewSort>> = KIO.comprehension {
        val total = !EventRegistrationRepo.countForView(params.search).orDie()
        val page = !EventRegistrationRepo.pageForView(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getEventRegistrationTemplate(
        eventId: UUID,
        clubId: UUID
    ): App<EventRegistrationError, ApiResponse.Dto<EventRegistrationTemplateDto>> = KIO.comprehension {
        val info = !EventRegistrationRepo.getEventRegistrationInfo(eventId).orDie()
            .onNullFail { EventRegistrationError.EventNotFound }

        val upsertableRegistration = !EventRegistrationRepo.getEventRegistrationForUpdate(eventId, clubId).orDie()
            .onNullFail { EventRegistrationError.EventNotFound }

        ok(EventRegistrationTemplateDto(info, upsertableRegistration)).map { ApiResponse.Dto(it) }
    }

    fun upsertRegistrationForEvent(
        eventId: UUID,
        registrationDto: EventRegistrationUpsertDto,
        user: AppUserWithPrivilegesRecord,
    ): App<EventRegistrationError, ApiResponse.Created> = KIO.comprehension {

        val template = !EventRegistrationRepo.getEventRegistrationInfo(eventId).orDie()

        // TODO Event is open for registrations OR is admin

        val now = LocalDateTime.now()

        val (persistedRegistrationId, isUpdate) =
            !EventRegistrationRepo.findByEventAndClub(eventId, user.club!!).map { it?.let { it.id to true } }.orDie()
                ?: (!EventRegistrationRepo.create(
                    EventRegistrationRecord(
                        UUID.randomUUID(),
                        eventId,
                        user.club!!,
                        registrationDto.message,
                        now,
                        user.id!!,
                        now,
                        user.id!!
                    )
                ).orDie() to false)

        if (isUpdate) {
            !EventRegistrationRepo.update(persistedRegistrationId) {
                message = registrationDto.message
                updatedAt = now
                updatedBy = user.id!!
            }.orDie()

            !CompetitionRegistrationRepo.deleteByEventRegistration(persistedRegistrationId).orDie()
        }

        val participantIdMap = !registrationDto.participants.traverse { pDto ->
            handleSingleCompetitionRegistration(pDto, user.id!!, user.club!!, template, persistedRegistrationId, now)
        }.map { it.toMap(mutableMapOf()) }

        !registrationDto.competitionRegistrations.traverse { competitionRegistrationDto ->
            handleTeamCompetitionRegistrations(
                template,
                competitionRegistrationDto,
                persistedRegistrationId,
                user.club!!,
                now,
                user.id!!,
                participantIdMap
            )
        }

        val eventName = !EventRepo.getName(eventId).orDie()
        val clubName = !ClubRepo.getName(user.club!!).orDie()
        val participants = !ParticipantForEventRepo.getByClub(user.club!!).orDie()

        //TODO: @refactor: after merging registration result, move to Repo, extract sorting
        val competitions = (!Jooq.query {
            fetch(EVENT_COMPETITION_REGISTRATION)
        }.orDie()).sortedWith { a, b ->
            val identA = a!!.identifier!!
            val identB = b!!.identifier!!

            val digitsA = identA.takeLastWhile { it.isDigit() }
            val digitsB = identB.takeLastWhile { it.isDigit() }

            val prefixA = identA.removeSuffix(digitsA)
            val prefixB = identB.removeSuffix(digitsB)

            val intA = digitsA.toIntOrNull() ?: 0
            val intB = digitsB.toIntOrNull() ?: 0

            // sort by lexicographical, except integer suffixes
            when {
                prefixA < prefixB -> -1
                prefixA > prefixB -> 1
                intA < intB -> -1
                intA > intB -> 1
                else -> 0
            }
        }

        val summaryParticipants = participants.joinToString("\n") { p ->
            """
                |    [${p.gender}] ${p.firstname} ${p.lastname}${p.year?.let { " ($it)" } ?: ""}${p.externalClubName?.let { " - $it" } ?: ""}
            """.trimMargin()
        }.trimMargin()

        val summaryCompetitions = competitions.joinToString("\n") { c ->
            val teams = c.clubRegistrations!!.flatMap { it!!.teams!!.map { it!! } }.filter { it.club == user.club }
            """
                |    ${c.identifier} ${c.name}${c.shortName?.let { " ($it)" } ?: ""}
                |        ${if (teams.isEmpty()) "---" else teams.joinToString("\n|        ") { t ->
                    val ps = t.participants!!.map { it!! }.sortedBy { it.role }
                    """
                        |->${t.teamName?.let { " ($it)" } ?: ""}
                        |            ${ps.joinToString("\n|            ") { p ->
                            """
                                |[${p.role}] ${p.firstname} ${p.lastname}
                            """.trimMargin()
                    }}
                    """.trimMargin()
            }}
                |
            """.trimMargin()
        }

        val content = !EmailService.getTemplate(
            EmailTemplateKey.EVENT_REGISTRATION_CONFIRMATION,
            EmailLanguage.valueOf(user.language!!)
        ).map { mailTemplate ->
            mailTemplate.toContent(
                EmailTemplatePlaceholder.RECIPIENT to user.firstname + " " + user.lastname,
                EmailTemplatePlaceholder.EVENT to (eventName ?: ""),
                EmailTemplatePlaceholder.CLUB to (clubName ?: ""),
                EmailTemplatePlaceholder.PARTICIPANTS to summaryParticipants,
                EmailTemplatePlaceholder.COMPETITIONS to summaryCompetitions,
            )
        }

        val attachments = !EventDocumentRepo.getDownloadsByEvent(eventId).orDie().map {
            it.map { rec ->
                EmailAttachment(
                    name = rec.name!!,
                    data = rec.data!!
                )
            }
        }

        !EmailService.enqueue(
            recipient = user.email!!,
            content = content,
            attachments = attachments
        )

        ok(ApiResponse.Created(persistedRegistrationId))

    }

    private fun handleSingleCompetitionRegistration(
        pDto: EventRegistrationParticipantUpsertDto,
        userId: UUID,
        clubId: UUID,
        template: EventRegistrationInfoDto?,
        persistedRegistrationId: UUID,
        now: LocalDateTime
    ): App<EventRegistrationError, Pair<UUID, UUID>> = KIO.comprehension {
        val persistedId = if (pDto.isNew == true) {
            !ParticipantRepo.create(!pDto.toRecord(userId, clubId)).orDie()
        } else {
            if (!!ParticipantRepo.existsByIdAndClub(pDto.id, clubId).orDie()) {
                KIO.fail(EventRegistrationError.InvalidRegistration)
            }

            if (pDto.hasChanged == true) {
                !ParticipantRepo.update(pDto.id) {
                    firstname = pDto.firstname
                    lastname = pDto.lastname
                    gender = pDto.gender
                    year = pDto.year
                    external = pDto.external
                    externalClubName = pDto.externalClubName?.trim()?.takeIf { it.isNotBlank() }
                }.orDie()
            }

            pDto.id
        }

        pDto.competitionsSingle?.traverse { competitionRegistrationDto ->

            val competition =
                template?.competitionsSingle?.first { it.id == competitionRegistrationDto.competitionId }
                    ?: return@traverse KIO.fail(EventRegistrationError.InvalidRegistration)

            val competitionRegistrationId = !CompetitionRegistrationRepo.create(
                CompetitionRegistrationRecord(
                    UUID.randomUUID(),
                    persistedRegistrationId,
                    competitionRegistrationDto.competitionId,
                    clubId,
                    null,
                    now,
                    userId,
                    now,
                    userId
                )
            ).orDie()

            !CompetitionRegistrationNamedParticipantRepo.create(
                CompetitionRegistrationNamedParticipantRecord(
                    competitionRegistrationId,
                    competition.namedParticipant?.first()?.id!!,
                    persistedId
                )
            ).orDie()

            competitionRegistrationDto.optionalFees?.traverse {
                handleOptionalFee(
                    competition,
                    competitionRegistrationId,
                    it
                )
            }?.not()

            unit

        }?.not()

        ok(pDto.id to persistedId)
    }

    private fun handleTeamCompetitionRegistrations(
        template: EventRegistrationInfoDto?,
        competitionRegistrationDto: CompetitionRegistrationUpsertDto,
        persistedRegistrationId: UUID,
        clubId: UUID,
        now: LocalDateTime,
        userId: UUID,
        participantIdMap: MutableMap<UUID, UUID>
    ): App<EventRegistrationError, Unit> = KIO.comprehension {

        val competition = template?.competitionsTeam?.first { it.id == competitionRegistrationDto.competitionId }
            ?: return@comprehension KIO.fail(EventRegistrationError.InvalidRegistration)

        competitionRegistrationDto.teams?.traverse { teamDto ->
            val competitionRegistrationId = !CompetitionRegistrationRepo.create(
                CompetitionRegistrationRecord(
                    UUID.randomUUID(),
                    persistedRegistrationId,
                    competitionRegistrationDto.competitionId,
                    clubId,
                    null,
                    now,
                    userId,
                    now,
                    userId
                )
            ).orDie()

            teamDto.namedParticipants.forEach { namedParticipantDto ->

                // TODO validate consistency

                namedParticipantDto.participantIds.forEach { participantId ->

                    val persistedId = participantIdMap[participantId]
                        ?: return@traverse KIO.fail(EventRegistrationError.InvalidRegistration)

                    !CompetitionRegistrationNamedParticipantRepo.create(
                        CompetitionRegistrationNamedParticipantRecord(
                            competitionRegistrationId,
                            namedParticipantDto.namedParticipantId,
                            persistedId
                        )
                    ).orDie()
                }

            }

            teamDto.optionalFees?.traverse { handleOptionalFee(competition, competitionRegistrationId, it) }?.not()

            unit

        }?.not()

        ok(Unit)
    }

    private fun handleOptionalFee(
        competition: EventRegistrationCompetitionDto,
        competitionRegistrationId: UUID,
        optionalFee: UUID
    ) = KIO.comprehension {

        if (competition.fees.find { it.id == optionalFee }?.required != false) {
            KIO.fail(EventRegistrationError.InvalidRegistration)
        }

        CompetitionRegistrationOptionalFeeRepo.create(
            CompetitionRegistrationOptionalFeeRecord(
                competitionRegistrationId,
                optionalFee
            )
        ).orDie()
    }
}