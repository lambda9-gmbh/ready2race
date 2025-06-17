package de.lambda9.ready2race.backend.app.eventRegistration.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationOptionalFeeRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationsWithoutTeamNumberDto
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
import de.lambda9.ready2race.backend.app.documentTemplate.control.DocumentTemplateRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.toPdfTemplate
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentType
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventRegistration.control.*
import de.lambda9.ready2race.backend.app.eventRegistration.entity.*
import de.lambda9.ready2race.backend.app.participant.control.ParticipantForEventRepo
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.lexiNumberComp
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.ok
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.*
import java.awt.Color
import java.io.ByteArrayOutputStream
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

object EventRegistrationService {

    fun pageView(
        params: PaginationParameters<EventRegistrationViewSort>,
    ): App<Nothing, ApiResponse.Page<EventRegistrationViewDto, EventRegistrationViewSort>> = KIO.comprehension {
        val total = !EventRegistrationRepo.countForView(params.search).orDie()
        val page = !EventRegistrationRepo.pageForView(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it, pagination = params.toPagination(total)
            )
        }
    }

    fun getEventRegistrationTemplate(
        eventId: UUID, clubId: UUID
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

        val (persistedRegistrationId, isUpdate) = !EventRegistrationRepo.findByEventAndClub(eventId, user.club!!)
            .map { it?.let { it.id to true } }.orDie() ?: (!EventRegistrationRepo.create(
            EventRegistrationRecord(
                UUID.randomUUID(), eventId, user.club!!, registrationDto.message, now, user.id!!, now, user.id!!
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

        val singleCompetitionMultipleCounts =
            registrationDto.participants.flatMap { it.competitionsSingle ?: emptyList() }
                .groupingBy { it.competitionId }.eachCount().filter { it.value > 1 }.mapValues { 0 }.toMutableMap()

        val participantIdMap = !registrationDto.participants.traverse { pDto ->
            handleSingleCompetitionRegistration(
                pDto, user.id!!, user.club!!, template, persistedRegistrationId, now, singleCompetitionMultipleCounts
            )
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

        //TODO: @refactor: move to Repo, !don't! query for ALL clubs
        val competitions = (!Jooq.query {
            fetch(EVENT_COMPETITION_REGISTRATION)
        }.orDie()).sortedWith(lexiNumberComp { it.identifier })

        val summaryParticipants = participants.joinToString("\n") { p ->
            """
                |    [${p.gender}] ${p.firstname} ${p.lastname}${p.year?.let { " ($it)" } ?: ""}${p.externalClubName?.let { " - $it" } ?: ""}
            """.trimMargin()
        }.trimMargin()

        val summaryCompetitions = competitions.joinToString("\n") { c ->
            val teams = c.clubRegistrations!!.flatMap { it!!.teams!!.map { it!! } }.filter { it.club == user.club }
            """
                |    ${c.identifier} ${c.name}${c.shortName?.let { " ($it)" } ?: ""}
                |        ${
                if (teams.isEmpty()) "---" else teams.sortedWith(lexiNumberComp { it.teamName })
                    .joinToString("\n|        ") { t ->
                        val ps = t.participants!!.map { it!! }.sortedBy { it.role }
                        """
                        |->${t.teamName?.let { " $it" } ?: ""}
                        |            ${
                            ps.joinToString("\n|            ") { p ->
                                """
                                |[${p.role}] ${p.firstname} ${p.lastname}
                            """.trimMargin()
                            }
                        }
                    """.trimMargin()
                    }
            }
                |
            """.trimMargin()
        }

        val content = !EmailService.getTemplate(
            EmailTemplateKey.EVENT_REGISTRATION_CONFIRMATION, EmailLanguage.valueOf(user.language!!)
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
                    name = rec.name!!, data = rec.data!!
                )
            }
        }

        !EmailService.enqueue(
            recipient = user.email!!, content = content, attachments = attachments
        )

        ok(ApiResponse.Created(persistedRegistrationId))

    }

    private fun handleSingleCompetitionRegistration(
        pDto: EventRegistrationParticipantUpsertDto,
        userId: UUID,
        clubId: UUID,
        template: EventRegistrationInfoDto?,
        persistedRegistrationId: UUID,
        now: LocalDateTime,
        singleCompetitionMultiCounts: MutableMap<UUID, Int>
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

            val competition = template?.competitionsSingle?.first { it.id == competitionRegistrationDto.competitionId }
                ?: return@traverse KIO.fail(EventRegistrationError.InvalidRegistration)

            val name = singleCompetitionMultiCounts[competitionRegistrationDto.competitionId]?.plus(1)?.let {
                singleCompetitionMultiCounts[competitionRegistrationDto.competitionId] = it
                "#$it"
            }

            val competitionRegistrationId = !CompetitionRegistrationRepo.create(
                CompetitionRegistrationRecord(
                    UUID.randomUUID(),
                    persistedRegistrationId,
                    competitionRegistrationDto.competitionId,
                    clubId,
                    name,
                    now,
                    userId,
                    now,
                    userId
                )
            ).orDie()

            !CompetitionRegistrationNamedParticipantRepo.create(
                CompetitionRegistrationNamedParticipantRecord(
                    competitionRegistrationId, competition.namedParticipant?.first()?.id!!, persistedId
                )
            ).orDie()

            competitionRegistrationDto.optionalFees?.traverse {
                handleOptionalFee(
                    competition, competitionRegistrationId, it
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

        var count = competitionRegistrationDto.teams?.takeIf { it.size > 1 }?.let { 0 }

        competitionRegistrationDto.teams?.traverse { teamDto ->

            val name = count?.plus(1)?.let {
                count = it
                "#$it"
            }

            val competitionRegistrationId = !CompetitionRegistrationRepo.create(
                CompetitionRegistrationRecord(
                    UUID.randomUUID(),
                    persistedRegistrationId,
                    competitionRegistrationDto.competitionId,
                    clubId,
                    name,
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
                            competitionRegistrationId, namedParticipantDto.namedParticipantId, persistedId
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
        competition: EventRegistrationCompetitionDto, competitionRegistrationId: UUID, optionalFee: UUID
    ) = KIO.comprehension {

        if (competition.fees.find { it.id == optionalFee }?.required != false) {
            KIO.fail(EventRegistrationError.InvalidRegistration)
        }

        CompetitionRegistrationOptionalFeeRepo.create(
            CompetitionRegistrationOptionalFeeRecord(
                competitionRegistrationId, optionalFee
            )
        ).orDie()
    }

    fun getRegistrationsWithoutTeamNumber(
        eventId: UUID
    ): App<EventError, ApiResponse.ListDto<CompetitionRegistrationsWithoutTeamNumberDto>> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val eventRegistrationResult = !EventRegistrationRepo.getRegistrationResult(eventId).orDie()

        val result = mutableListOf<CompetitionRegistrationsWithoutTeamNumberDto>()

        eventRegistrationResult?.competitions?.forEach { competition ->
            competition?.clubRegistrations?.forEach { clubRegistration ->
                clubRegistration?.teams?.forEach { registrationTeam ->
                    if (registrationTeam?.teamNumber == null) {
                        result.add(
                            CompetitionRegistrationsWithoutTeamNumberDto(
                                competitionId = competition.id!!,
                                competitionIdentifier = competition.identifier!!,
                                competitionName = competition.name!!,
                                registrationId = registrationTeam?.id!!,
                                registrationClub = clubRegistration.name!!,
                                registrationName = registrationTeam.teamName,
                            )
                        )
                    }
                }
            }
        }

        ok(
            ApiResponse.ListDto(result)
        )
    }

    fun finalizeRegistrations(
        userId: UUID,
        eventId: UUID,
        keepNumbers: Boolean
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val registrations = !CompetitionRegistrationRepo.allForEvent(eventId).orDie()

        registrations.groupBy { it.competition }.values.forEach { registrationsForSameComp ->
            if (keepNumbers) {

                val highestNumber = registrationsForSameComp.mapNotNull { it.teamNumber }.maxOfOrNull { it } ?: 0

                registrationsForSameComp
                    .filter { it.teamNumber == null }
                    .shuffled()
                    .forEachIndexed { idx, record ->
                        record.teamNumber = highestNumber + idx + 1
                        record.updatedBy = userId
                        record.updatedAt = LocalDateTime.now()
                        record.update()
                    }
            } else {
                registrationsForSameComp.shuffled().forEachIndexed { idx, record ->
                    record.teamNumber = idx + 1
                    record.updatedBy = userId
                    record.updatedAt = LocalDateTime.now()
                    record.update()
                }
            }
        }

        !EventRegistrationReportRepo.delete(eventId).orDie()

        !generateResultDocument(eventId)

        noData
    }

    fun downloadResult(
        eventId: UUID,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        !EventService.checkEventExisting(eventId)

        EventRegistrationReportRepo.getDownload(eventId).orDie()
            .onNullFail { EventRegistrationError.RegistrationsNotFinalized }.map {
                ApiResponse.File(
                    name = it.name!!,
                    bytes = it.data!!,
                )
            }
    }

    private fun generateResultDocument(
        eventId: UUID,
    ): App<EventRegistrationError, Pair<String, ByteArray>> = KIO.comprehension {

        val result = !EventRegistrationRepo.getRegistrationResult(eventId).orDie()
            .onNullFail { EventRegistrationError.EventNotFound }

        val pdfTemplate = !DocumentTemplateRepo.getAssigned(DocumentType.REGISTRATION_REPORT, eventId).orDie()
            .andThenNotNull { it.toPdfTemplate() }

        val doc = document(pdfTemplate) {
            // TODO: Instead don't allow this action
            if (result.competitions!!.isEmpty()) {
                page {
                    text { "keine Wettkämpfe in dieser Veranstaltung" }
                }
            }
            result.competitions!!.sortedWith(lexiNumberComp { it?.identifier }).forEach { competition ->
                competition!!
                page {
                    block(
                        padding = Padding(0f, 0f, 0f, 20f)
                    ) {
                        text(
                            fontStyle = FontStyle.BOLD,
                            fontSize = 14f,
                        ) {
                            "Wettkampf / "
                        }
                        text(
                            fontSize = 12f,
                            newLine = false,
                        ) {
                            "Competition"
                        }

                        table(
                            padding = Padding(5f, 10f, 0f, 0f)
                        ) {
                            column(0.1f)
                            column(0.25f)
                            column(0.65f)

                            row {
                                cell {
                                    text(
                                        fontSize = 12f,
                                    ) { competition.identifier!! }
                                }
                                cell {
                                    competition.shortName?.let {
                                        text(
                                            fontSize = 12f,
                                        ) { it }
                                    }
                                }
                                cell {
                                    text(
                                        fontSize = 12f,
                                    ) { competition.name!! }
                                }
                            }
                        }
                    }

                    if (competition.clubRegistrations!!.isEmpty()) {
                        text(
                            fontStyle = FontStyle.BOLD,
                            fontSize = 11f,
                        ) { "Wettkampf entfällt / " }
                        text(
                            newLine = false,
                        ) { "Competition cancelled" }
                    } else {
                        competition.clubRegistrations!!.forEach { club ->
                            club!!.teams!!.sortedWith(lexiNumberComp { it?.teamName }).forEach { team ->
                                team!!
                                block(
                                    padding = Padding(0f, 0f, 0f, 15f)
                                ) {

                                    text(
                                        fontStyle = FontStyle.BOLD
                                    ) { club.name!! }
                                    team.teamName?.let {
                                        text(
                                            newLine = false,
                                        ) { " $it" }
                                    }

                                    table(
                                        padding = Padding(5f, 0f, 0f, 0f),
                                        withBorder = true,
                                    ) {
                                        column(0.15f)
                                        column(0.2f)
                                        column(0.2f)
                                        column(0.1f)
                                        column(0.35f)

                                        team.participants!!
                                            .sortedBy { it!!.role }
                                            .forEachIndexed { idx, member ->
                                                row(
                                                    color = if (idx % 2 == 1) Color(230, 230, 230) else null,
                                                ) {
                                                    cell {
                                                        text { member!!.role!! }
                                                    }
                                                    cell {
                                                        text { member!!.firstname!! }
                                                    }
                                                    cell {
                                                        text { member!!.lastname!! }
                                                    }
                                                    cell {
                                                        text { member!!.year!!.toString() }
                                                    }
                                                    cell {
                                                        text { member!!.externalClubName ?: club.name!! }
                                                    }
                                                }
                                            }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()

        val filename = "registration_result_${result.eventName!!.replace(" ", "-")}_${
            LocalDateTime.now().format(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
            )
        }.pdf"

        val bytes = out.toByteArray()
        out.close()

        val documentRecord = EventRegistrationReportRecord(
            event = eventId,
            name = filename,
            createdAt = LocalDateTime.now(),
        )
        val id = !EventRegistrationReportRepo.create(documentRecord).orDie()
        val dataRecord = EventRegistrationReportDataRecord(
            resultDocument = id,
            data = bytes,
        )
        !EventRegistrationReportDataRepo.create(dataRecord).orDie()

        ok(filename to bytes)
    }
}