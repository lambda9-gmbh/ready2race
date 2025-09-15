package de.lambda9.ready2race.backend.app.webDAV.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.toDto
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportRequest
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportStatusDto
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.app.webDAV.entity.bankAccounts.BankAccountExport
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionCategories.CompetitionCategoryExport
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionProperties.CompetitionPropertiesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionProperties.CompetitionPropertiesHasFeeExport
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionProperties.CompetitionPropertiesHasNamedParticipantExport
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionSetup.*
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionSetupTemplates.CompetitionSetupTemplateExport
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionTemplates.CompetitionTemplateExport
import de.lambda9.ready2race.backend.app.webDAV.entity.contactInformation.ContactInformationExport
import de.lambda9.ready2race.backend.app.webDAV.entity.emailIndividualTemplates.EmailIndividualTemplateExport
import de.lambda9.ready2race.backend.app.webDAV.entity.eventDocumentTypes.EventDocumentTypeExport
import de.lambda9.ready2race.backend.app.webDAV.entity.fees.FeeExport
import de.lambda9.ready2race.backend.app.webDAV.entity.matchResultsImportConfigs.MatchResultImportConfigExport
import de.lambda9.ready2race.backend.app.webDAV.entity.namedParticipants.NamedParticipantExport
import de.lambda9.ready2race.backend.app.webDAV.entity.participantRequirements.ParticipantRequirementExport
import de.lambda9.ready2race.backend.app.webDAV.entity.participants.ParticipantExport
import de.lambda9.ready2race.backend.app.webDAV.entity.ratingCategories.RatingCategoryExport
import de.lambda9.ready2race.backend.app.webDAV.entity.startlistExportConfigs.StartlistExportConfigExport
import de.lambda9.ready2race.backend.app.webDAV.entity.users.*
import de.lambda9.ready2race.backend.app.webDAV.entity.workTypes.WorkTypeExport
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

// COMPETITION SETUP CONVERSIONS

fun CompetitionSetupTemplateRecord.toExport(): App<Nothing, CompetitionSetupTemplateExport> = KIO.ok(
    CompetitionSetupTemplateExport(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun CompetitionSetupTemplateExport.toRecord(): App<Nothing, CompetitionSetupTemplateRecord> = KIO.ok(
    CompetitionSetupTemplateRecord(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )
)

fun CompetitionSetupRoundRecord.toExport(): App<Nothing, CompetitionSetupRoundExport> = KIO.ok(
    CompetitionSetupRoundExport(
        id = id,
        competitionSetup = competitionSetup,
        competitionSetupTemplate = competitionSetupTemplate,
        nextRound = nextRound,
        name = name,
        required = required,
        useDefaultSeeding = useDefaultSeeding,
        placesOption = placesOption
    )
)

fun CompetitionSetupRoundExport.toRecord(): App<Nothing, CompetitionSetupRoundRecord> = KIO.ok(
    CompetitionSetupRoundRecord(
        id = id,
        competitionSetup = competitionSetup,
        competitionSetupTemplate = competitionSetupTemplate,
        nextRound = nextRound,
        name = name,
        required = required,
        useDefaultSeeding = useDefaultSeeding,
        placesOption = placesOption
    )
)

fun CompetitionSetupGroupRecord.toExport(): App<Nothing, CompetitionSetupGroupExport> = KIO.ok(
    CompetitionSetupGroupExport(
        id = id,
        weighting = weighting,
        teams = teams,
        name = name
    )
)

fun CompetitionSetupGroupExport.toRecord(): App<Nothing, CompetitionSetupGroupRecord> = KIO.ok(
    CompetitionSetupGroupRecord(
        id = id,
        weighting = weighting,
        teams = teams,
        name = name
    )
)

fun CompetitionSetupGroupStatisticEvaluationRecord.toExport(): App<Nothing, CompetitionSetupGroupStatisticEvaluationExport> =
    KIO.ok(
        CompetitionSetupGroupStatisticEvaluationExport(
            competitionSetupRound = competitionSetupRound,
            name = name,
            priority = priority,
            rankByBiggest = rankByBiggest,
            ignoreBiggestValues = ignoreBiggestValues,
            ignoreSmallestValues = ignoreSmallestValues,
            asAverage = asAverage
        )
    )

fun CompetitionSetupGroupStatisticEvaluationExport.toRecord(): App<Nothing, CompetitionSetupGroupStatisticEvaluationRecord> =
    KIO.ok(
        CompetitionSetupGroupStatisticEvaluationRecord(
            competitionSetupRound = competitionSetupRound,
            name = name,
            priority = priority,
            rankByBiggest = rankByBiggest,
            ignoreBiggestValues = ignoreBiggestValues,
            ignoreSmallestValues = ignoreSmallestValues,
            asAverage = asAverage
        )
    )

fun CompetitionSetupMatchRecord.toExport(): App<Nothing, CompetitionSetupMatchExport> = KIO.ok(
    CompetitionSetupMatchExport(
        id = id,
        competitionSetupRound = competitionSetupRound,
        competitionSetupGroup = competitionSetupGroup,
        weighting = weighting,
        teams = teams,
        name = name,
        executionOrder = executionOrder,
        startTimeOffset = startTimeOffset
    )
)

fun CompetitionSetupMatchExport.toRecord(): App<Nothing, CompetitionSetupMatchRecord> = KIO.ok(
    CompetitionSetupMatchRecord(
        id = id,
        competitionSetupRound = competitionSetupRound,
        competitionSetupGroup = competitionSetupGroup,
        weighting = weighting,
        teams = teams,
        name = name,
        executionOrder = executionOrder,
        startTimeOffset = startTimeOffset
    )
)

fun CompetitionSetupParticipantRecord.toExport(): App<Nothing, CompetitionSetupParticipantExport> = KIO.ok(
    CompetitionSetupParticipantExport(
        id = id,
        competitionSetupMatch = competitionSetupMatch,
        competitionSetupGroup = competitionSetupGroup,
        seed = seed,
        ranking = ranking
    )
)

fun CompetitionSetupParticipantExport.toRecord(): App<Nothing, CompetitionSetupParticipantRecord> = KIO.ok(
    CompetitionSetupParticipantRecord(
        id = id,
        competitionSetupMatch = competitionSetupMatch,
        competitionSetupGroup = competitionSetupGroup,
        seed = seed,
        ranking = ranking
    )
)

fun CompetitionSetupPlaceRecord.toExport(): App<Nothing, CompetitionSetupPlaceExport> = KIO.ok(
    CompetitionSetupPlaceExport(
        competitionSetupRound = competitionSetupRound,
        roundOutcome = roundOutcome,
        place = place
    )
)

fun CompetitionSetupPlaceExport.toRecord(): App<Nothing, CompetitionSetupPlaceRecord> = KIO.ok(
    CompetitionSetupPlaceRecord(
        competitionSetupRound = competitionSetupRound,
        roundOutcome = roundOutcome,
        place = place
    )
)

fun CompetitionTemplateRecord.toExport(): App<Nothing, CompetitionTemplateExport> = KIO.ok(
    CompetitionTemplateExport(
        id = id,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy,
        competitionSetupTemplate = competitionSetupTemplate,
    )
)

fun CompetitionTemplateExport.toRecord(): App<Nothing, CompetitionTemplateRecord> = KIO.ok(
    CompetitionTemplateRecord(
        id = id,
        createdAt = createdAt,
        createdBy = createdBy,
        updatedAt = updatedAt,
        updatedBy = updatedBy,
        competitionSetupTemplate = competitionSetupTemplate,
    )
)

fun CompetitionPropertiesRecord.toExport(): App<Nothing, CompetitionPropertiesExport> = KIO.ok(
    CompetitionPropertiesExport(
        id = id,
        competition = competition,
        competitionTemplate = competitionTemplate,
        identifier = identifier,
        name = name,
        shortName = shortName,
        description = description,
        competitionCategory = competitionCategory,
        lateRegistrationAllowed = lateRegistrationAllowed,
    )
)

fun CompetitionPropertiesExport.toRecord(): App<Nothing, CompetitionPropertiesRecord> = KIO.ok(
    CompetitionPropertiesRecord(
        id = id,
        competition = competition,
        competitionTemplate = competitionTemplate,
        identifier = identifier,
        name = name,
        shortName = shortName,
        description = description,
        competitionCategory = competitionCategory,
        lateRegistrationAllowed = lateRegistrationAllowed,
    )
)

fun CompetitionPropertiesHasFeeRecord.toExport(): App<Nothing, CompetitionPropertiesHasFeeExport> = KIO.ok(
    CompetitionPropertiesHasFeeExport(
        id = id,
        competitionProperties = competitionProperties,
        fee = fee,
        required = required,
        amount = amount,
        lateAmount = lateAmount,
    )
)

fun CompetitionPropertiesHasFeeExport.toRecord(): App<Nothing, CompetitionPropertiesHasFeeRecord> = KIO.ok(
    CompetitionPropertiesHasFeeRecord(
        id = id,
        competitionProperties = competitionProperties,
        fee = fee,
        required = required,
        amount = amount,
        lateAmount = lateAmount,
    )
)

fun CompetitionPropertiesHasNamedParticipantRecord.toExport(): App<Nothing, CompetitionPropertiesHasNamedParticipantExport> =
    KIO.ok(
        CompetitionPropertiesHasNamedParticipantExport(
            competitionProperties = competitionProperties,
            namedParticipant = namedParticipant,
            countMales = countMales,
            countFemales = countFemales,
            countNonBinary = countNonBinary,
            countMixed = countMixed,
        )
    )

fun CompetitionPropertiesHasNamedParticipantExport.toRecord(): App<Nothing, CompetitionPropertiesHasNamedParticipantRecord> =
    KIO.ok(
        CompetitionPropertiesHasNamedParticipantRecord(
            competitionProperties = competitionProperties,
            namedParticipant = namedParticipant,
            countMales = countMales,
            countFemales = countFemales,
            countNonBinary = countNonBinary,
            countMixed = countMixed,
        )
    )