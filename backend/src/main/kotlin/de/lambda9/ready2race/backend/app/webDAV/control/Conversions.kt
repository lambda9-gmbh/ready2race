package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.toDto
import de.lambda9.ready2race.backend.app.webDAV.entity.*
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun WebDAVExportRequest.toRecord(
    userId: UUID,
): App<Nothing, WebdavExportProcessRecord> = KIO.ok(
    WebdavExportProcessRecord(
        id = UUID.randomUUID(),
        name = name,
        createdAt = LocalDateTime.now(),
        createdBy = userId,
    )
)

fun WebdavExportProcessStatusRecord.toDto(
    events: List<String>,
    exportTypes: List<WebDAVExportType>,
    filesExported: Int,
    filesWithError: Int,
): App<Nothing, WebDAVExportStatusDto> = KIO.comprehension {
    val createdByDto = createdBy?.let { !it.toDto() }

    KIO.ok(
        WebDAVExportStatusDto(
            processId = id!!,
            exportFolderName = name!!,
            exportInitializedAt = createdAt!!,
            exportInitializedBy = createdByDto,
            events = events,
            exportTypes = exportTypes,
            filesExported = filesExported,
            totalFilesToExport = fileExports!!.size,
            filesWithError = filesWithError
        )
    )
}


// EXPORT CONVERSIONS

fun AppUserRecord.toExport(): App<Nothing, AppUserExport> = KIO.ok(
    AppUserExport(
        id = id,
        email = email,
        firstname = firstname,
        lastname = lastname,
        language = language,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy,
        club = club
    )
)

fun AppUserExport.toRecord(password: String): App<Nothing, AppUserRecord> =
    PasswordUtilities.hash(password).map { hashed ->
        AppUserRecord(
            id = id,
            email = email,
            password = hashed,
            firstname = firstname,
            lastname = lastname,
            language = language,
            createdAt = createdAt,
            createdBy = createdBy,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
            club = club
        )
    }

fun RoleRecord.toExport(): App<Nothing, RoleExport> = KIO.ok(
    RoleExport(
        id = id,
        name = name,
        description = description,
        static = static,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun RoleExport.toRecord(): App<Nothing, RoleRecord> = KIO.ok(
    RoleRecord(
        id = id,
        name = name,
        description = description,
        static = static,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun RoleHasPrivilegeRecord.toExport(): App<Nothing, RoleHasPrivilegeExport> = KIO.ok(
    RoleHasPrivilegeExport(
        role = role,
        privilege = privilege
    )
)

fun RoleHasPrivilegeExport.toRecord(): App<Nothing, RoleHasPrivilegeRecord> = KIO.ok(
    RoleHasPrivilegeRecord(
        role = role,
        privilege = privilege
    )
)

fun AppUserHasRoleRecord.toExport(): App<Nothing, AppUserHasRoleExport> = KIO.ok(
    AppUserHasRoleExport(
        appUser = appUser,
        role = role
    )
)

fun AppUserHasRoleExport.toRecord(): App<Nothing, AppUserHasRoleRecord> = KIO.ok(
    AppUserHasRoleRecord(
        appUser = appUser,
        role = role
    )
)

fun ClubRecord.toExport(): App<Nothing, ClubExport> = KIO.ok(
    ClubExport(
        id = id,
        name = name,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun ClubExport.toRecordWithoutUsers(): App<Nothing, ClubRecord> = KIO.ok(
    ClubRecord(
        id = id,
        name = name,
        createdAt = createdAt,
        createdBy = null,
        updatedAt = updatedAt,
        updatedBy = null
    )
)

fun ParticipantRecord.toExport(): App<Nothing, ParticipantExport> = KIO.ok(
    ParticipantExport(
        id = id,
        club = club,
        firstname = firstname,
        lastname = lastname,
        year = year,
        gender = gender.toString(),
        phone = phone,
        external = external,
        externalClubName = externalClubName,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun ParticipantExport.toRecord(): App<Nothing, ParticipantRecord> = KIO.ok(
    ParticipantRecord(
        id = id,
        club = club,
        firstname = firstname,
        lastname = lastname,
        year = year,
        gender = Gender.valueOf(gender),
        phone = phone,
        external = external,
        externalClubName = externalClubName,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun BankAccountRecord.toExport(): App<Nothing, BankAccountExport> = KIO.ok(
    BankAccountExport(
        id = id,
        holder = holder,
        iban = iban,
        bic = bic,
        bank = bank,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun BankAccountExport.toRecord(): App<Nothing, BankAccountRecord> = KIO.ok(
    BankAccountRecord(
        id = id,
        holder = holder,
        iban = iban,
        bic = bic,
        bank = bank,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun ContactInformationRecord.toExport(): App<Nothing, ContactInformationExport> = KIO.ok(
    ContactInformationExport(
        id = id,
        name = name,
        addressZip = addressZip,
        addressCity = addressCity,
        addressStreet = addressStreet,
        email = email,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun ContactInformationExport.toRecord(): App<Nothing, ContactInformationRecord> = KIO.ok(
    ContactInformationRecord(
        id = id,
        name = name,
        addressZip = addressZip,
        addressCity = addressCity,
        addressStreet = addressStreet,
        email = email,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun MatchResultImportConfigRecord.toExport(): App<Nothing, MatchResultImportConfigExport> = KIO.ok(
    MatchResultImportConfigExport(
        id = id,
        name = name,
        colTeamStartNumber = colTeamStartNumber,
        colTeamPlace = colTeamPlace,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun MatchResultImportConfigExport.toRecord(): App<Nothing, MatchResultImportConfigRecord> = KIO.ok(
    MatchResultImportConfigRecord(
        id = id,
        name = name,
        colTeamStartNumber = colTeamStartNumber,
        colTeamPlace = colTeamPlace,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun StartlistExportConfigRecord.toExport(): App<Nothing, StartlistExportConfigExport> = KIO.ok(
    StartlistExportConfigExport(
        id = id,
        name = name,
        colParticipantFirstname = colParticipantFirstname,
        colParticipantLastname = colParticipantLastname,
        colParticipantGender = colParticipantGender,
        colParticipantRole = colParticipantRole,
        colParticipantYear = colParticipantYear,
        colParticipantClub = colParticipantClub,
        colClubName = colClubName,
        colTeamName = colTeamName,
        colTeamStartNumber = colTeamStartNumber,
        colMatchName = colMatchName,
        colMatchStartTime = colMatchStartTime,
        colRoundName = colRoundName,
        colCompetitionIdentifier = colCompetitionIdentifier,
        colCompetitionName = colCompetitionName,
        colCompetitionShortName = colCompetitionShortName,
        colCompetitionCategory = colCompetitionCategory,
        colTeamRatingCategory = colTeamRatingCategory,
        colTeamClub = colTeamClub,
        colTeamDeregistered = colTeamDeregistered,
        valueTeamDeregistered = valueTeamDeregistered,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun StartlistExportConfigExport.toRecord(): App<Nothing, StartlistExportConfigRecord> = KIO.ok(
    StartlistExportConfigRecord(
        id = id,
        name = name,
        colParticipantFirstname = colParticipantFirstname,
        colParticipantLastname = colParticipantLastname,
        colParticipantGender = colParticipantGender,
        colParticipantRole = colParticipantRole,
        colParticipantYear = colParticipantYear,
        colParticipantClub = colParticipantClub,
        colClubName = colClubName,
        colTeamName = colTeamName,
        colTeamStartNumber = colTeamStartNumber,
        colMatchName = colMatchName,
        colMatchStartTime = colMatchStartTime,
        colRoundName = colRoundName,
        colCompetitionIdentifier = colCompetitionIdentifier,
        colCompetitionName = colCompetitionName,
        colCompetitionShortName = colCompetitionShortName,
        colCompetitionCategory = colCompetitionCategory,
        colTeamRatingCategory = colTeamRatingCategory,
        colTeamClub = colTeamClub,
        colTeamDeregistered = colTeamDeregistered,
        valueTeamDeregistered = valueTeamDeregistered,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun WorkTypeRecord.toExport(): App<Nothing, WorkTypeExport> = KIO.ok(
    WorkTypeExport(
        id = id,
        name = name,
        description = description,
        color = color,
        minUser = minUser,
        maxUser = maxUser,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun WorkTypeExport.toRecord(): App<Nothing, WorkTypeRecord> = KIO.ok(
    WorkTypeRecord(
        id = id,
        name = name,
        description = description,
        color = color,
        minUser = minUser,
        maxUser = maxUser,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun ParticipantRequirementRecord.toExport(): App<Nothing, ParticipantRequirementExport> = KIO.ok(
    ParticipantRequirementExport(
        id = id,
        name = name,
        description = description,
        optional = optional,
        checkInApp = checkInApp,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun ParticipantRequirementExport.toRecord(): App<Nothing, ParticipantRequirementRecord> = KIO.ok(
    ParticipantRequirementRecord(
        id = id,
        name = name,
        description = description,
        optional = optional,
        checkInApp = checkInApp,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun RatingCategoryRecord.toExport(): App<Nothing, RatingCategoryExport> = KIO.ok(
    RatingCategoryExport(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun RatingCategoryExport.toRecord(): App<Nothing, RatingCategoryRecord> = KIO.ok(
    RatingCategoryRecord(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun CompetitionCategoryRecord.toExport(): App<Nothing, CompetitionCategoryExport> = KIO.ok(
    CompetitionCategoryExport(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun CompetitionCategoryExport.toRecord(): App<Nothing, CompetitionCategoryRecord> = KIO.ok(
    CompetitionCategoryRecord(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun FeeRecord.toExport(): App<Nothing, FeeExport> = KIO.ok(
    FeeExport(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun FeeExport.toRecord(): App<Nothing, FeeRecord> = KIO.ok(
    FeeRecord(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun NamedParticipantRecord.toExport(): App<Nothing, NamedParticipantExport> = KIO.ok(
    NamedParticipantExport(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun NamedParticipantExport.toRecord(): App<Nothing, NamedParticipantRecord> = KIO.ok(
    NamedParticipantRecord(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun EmailIndividualTemplateRecord.toExport(): App<Nothing, EmailIndividualTemplateExport> = KIO.ok(
    EmailIndividualTemplateExport(
        key = key,
        language = language,
        subject = subject,
        body = body,
        bodyIsHtml = bodyIsHtml,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun EmailIndividualTemplateExport.toRecord(): App<Nothing, EmailIndividualTemplateRecord> = KIO.ok(
    EmailIndividualTemplateRecord(
        key = key,
        language = language,
        subject = subject,
        body = body,
        bodyIsHtml = bodyIsHtml,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun EventDocumentTypeRecord.toExport(): App<Nothing, EventDocumentTypeExport> = KIO.ok(
    EventDocumentTypeExport(
        id = id,
        name = name,
        required = required,
        confirmationRequired = confirmationRequired,
        description = description,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun EventDocumentTypeExport.toRecord(): App<Nothing, EventDocumentTypeRecord> = KIO.ok(
    EventDocumentTypeRecord(
        id = id,
        name = name,
        required = required,
        confirmationRequired = confirmationRequired,
        description = description,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)