package de.lambda9.ready2race.backend.app.webDAV.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.checkRequestTypeDependencies
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.webDAVExportTypeDependencies
import de.lambda9.ready2race.backend.app.webDAV.control.*
import de.lambda9.ready2race.backend.app.webDAV.entity.*
import de.lambda9.ready2race.backend.app.webDAV.entity.DataBankAccountsExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataCompetitionExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataCompetitionCategoriesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataCompetitionSetupTemplatesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataCompetitionTemplatesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataContactInformationExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataEmailIndividualTemplatesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataEventExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataEventDocumentTypesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataFeesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataMatchResultImportConfigsExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataNamedParticipantsExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataParticipantRequirementsExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataParticipantsExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataRatingCategoriesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataStartlistExportConfigsExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataUsersExport
import de.lambda9.ready2race.backend.app.webDAV.entity.DataWorkTypesExport
import de.lambda9.ready2race.backend.calls.comprehension.CallComprehensionScope
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavImportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavImportDependencyRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavImportProcessRecord
import de.lambda9.ready2race.backend.kio.accessConfig
import de.lambda9.ready2race.backend.kio.comprehension
import de.lambda9.ready2race.backend.schedule.DynamicIntervalJobState
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.run
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.transact
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime
import java.util.*

object WebDAVImportService {

    suspend fun CallComprehensionScope.getImportOptionFolders(): App<WebDAVError.WebDAVExternError, ApiResponse.ListDto<String>> {
        val config = !accessConfig()
        if (config.webDAV == null) {
            return KIO.fail(WebDAVError.ConfigIncomplete)
        }

        val client = HttpClient(CIO)
        val authHeader = WebDAVService.buildBasicAuthHeader(config.webDAV)

        val propfindUrl = WebDAVService.getUrl(
            webDAVConfig = config.webDAV,
            pathSegments = ""
        )

        val propfindBody = """
                        <?xml version="1.0"?>
                        <d:propfind xmlns:d="DAV:">
                            <d:prop>
                                <d:resourcetype/>
                                <d:displayname/>
                            </d:prop>
                        </d:propfind>
                    """.trimIndent()

        val resp = client.request(propfindUrl) {
            method = HttpMethod("PROPFIND")
            header("Authorization", authHeader)
            header("Depth", "1")
            setBody(propfindBody)
            contentType(ContentType.Application.Xml)
        }

        !KIO.failOn(!resp.status.isSuccess()) {
            client.close()
            WebDAVError.CannotListFolders
        }

        val responseBody = resp.bodyAsText()
        val folderNames = parseFolderNamesFromPropfind(responseBody)

        client.close()

        return KIO.ok(ApiResponse.ListDto(folderNames))
    }

    private fun parseFolderNamesFromPropfind(xmlResponse: String): List<String> {
        val folderNames = mutableListOf<String>()

        // Use regex to find all response blocks
        val responsePattern = "<d:response>(.*?)</d:response>".toRegex()
        val matches = responsePattern.findAll(xmlResponse)

        for (match in matches) {
            val href = match.groupValues[0]
            val responseContent = match.groupValues[1]

            // Check if this is a collection (folder)
            val isCollection = responseContent.contains("<d:collection/>") ||
                responseContent.contains("<d:collection></d:collection>")

            if (isCollection) {
                // Extract display name
                val displayNamePattern = "<d:displayname>(.*?)</d:displayname>".toRegex()
                val displayNameMatch = displayNamePattern.find(responseContent)

                if (displayNameMatch != null) {
                    val displayName = displayNameMatch.groupValues[1]
                    if (displayName.isNotEmpty()) {
                        folderNames.add(displayName)
                    }
                }
            }
        }

        // Remove root folder
        return folderNames.drop(1).distinct()
    }

    suspend fun CallComprehensionScope.getImportOptionTypes(folderName: String): App<WebDAVError.WebDAVExternError, ApiResponse.Dto<WebDAVImportOptionsDto>> {
        val config = !accessConfig()
        if (config.webDAV == null) {
            return KIO.fail(WebDAVError.ConfigIncomplete)
        }

        val client = HttpClient(CIO)
        val authHeader = WebDAVService.buildBasicAuthHeader(config.webDAV)

        val manifestUrl = WebDAVService.getUrl(
            webDAVConfig = config.webDAV,
            pathSegments = "$folderName/manifest.json"
        )

        val manifestResponse = client.request(manifestUrl) {
            method = HttpMethod.Get
            header("Authorization", authHeader)
        }

        if (!manifestResponse.status.isSuccess()) {
            client.close()
            return KIO.fail(WebDAVError.ManifestNotFound)
        }

        val manifestContent = manifestResponse.bodyAsText()

        // Parse the manifest JSON into ManifestExport data class
        val manifest = try {
            jsonMapper.readValue(manifestContent, ManifestExport::class.java)
        } catch (e: Exception) {
            client.close()
            return KIO.fail(WebDAVError.ManifestParsingFailed)
        }

        client.close()

        return KIO.ok(
            ApiResponse.Dto(
                WebDAVImportOptionsDto(
                    data = manifest.exportedTypes,
                    events = manifest.exportedEvents.map { event ->
                        WebDAVImportOptionsDto.WebDAVImportOptionsEventDto(
                            eventId = event.eventId,
                            eventFolderName = event.folderName,
                            competitions = event.competitions.map { comp ->
                                WebDAVImportOptionsDto.WebDAVImportOptionsCompetitionDto(
                                    competitionId = comp.competitionId,
                                    competitionFolderName = comp.folderName
                                )
                            }
                        )
                    }
                )))
    }


    fun initializeImportData(
        request: WebDAVImportRequest,
        userId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !checkRequestTypeDependencies(
            request.selectedData + if (request.selectedEvents.isNotEmpty()) {
                listOf(WebDAVExportType.DB_EVENT) + if (request.selectedEvents.any { it.competitionFolderNames.isNotEmpty() }) {
                    listOf(WebDAVExportType.DB_COMPETITION)
                } else listOf()
            } else listOf()
        )

        val importProcess = WebdavImportProcessRecord(
            id = UUID.randomUUID(),
            importFolderName = request.folderName,
            createdAt = LocalDateTime.now(),
            createdBy = userId
        )
        val processId = !WebDAVImportProcessRepo.create(importProcess).orDie()


        fun buildImportDataRecord(
            dataType: WebDAVExportType,
            customPath: String? = null
        ): WebdavImportDataRecord {
            return WebdavImportDataRecord(
                id = UUID.randomUUID(),
                webdavImportProcess = processId,
                documentType = dataType.name,
                path = customPath ?: "${request.folderName}/${WebDAVService.getWebDavDataJsonFileName(dataType)}",
                importedAt = null,
                errorAt = null,
                error = null
            )
        }

        val importDataRecords = mutableListOf<WebdavImportDataRecord>()
        val importDataIds = mutableMapOf<WebDAVExportType, UUID>()


        val dependencyRecords = mutableListOf<WebdavImportDependencyRecord>()


        // Create import data records for each selected type
        request.selectedData.forEach { dataType ->
            val record = buildImportDataRecord(dataType)
            importDataRecords.add(record)
            importDataIds[dataType] = record.id
        }

        request.selectedEvents.forEach { event ->
            val eventImportRecord = buildImportDataRecord(
                dataType = WebDAVExportType.DB_EVENT,
                customPath = "${request.folderName}/${event.eventFolderName}/${
                    WebDAVService.getWebDavDataJsonFileName(
                        WebDAVExportType.DB_EVENT
                    )
                }"
            )
            importDataRecords.add(eventImportRecord)
            event.competitionFolderNames.forEach { competitionFolderName ->
                val competitionImportRecord = buildImportDataRecord(
                    dataType = WebDAVExportType.DB_COMPETITION,
                    customPath = "${request.folderName}/${event.eventFolderName}/${
                        WebDAVExportService.getEventContentFolderName(
                            WebDAVExportType.DB_COMPETITION
                        )
                    }/${
                        competitionFolderName
                    }/${
                        WebDAVService.getWebDavDataJsonFileName(
                            WebDAVExportType.DB_COMPETITION
                        )
                    }"
                )
                importDataRecords.add(competitionImportRecord)

                // Add dependencies from competition -> event
                dependencyRecords.add(
                    WebdavImportDependencyRecord(
                        webdavImportData = competitionImportRecord.id,
                        dependingOn = eventImportRecord.id
                    )
                )
            }
        }

        // Create import data records
        !WebDAVImportDataRepo.create(importDataRecords).orDie()

        // add dependencies between types
        request.selectedData.forEach { dataType ->
            webDAVExportTypeDependencies[dataType]?.forEach { dependency ->
                if (dataType != WebDAVExportType.DB_COMPETITION || dependency != WebDAVExportType.DB_EVENT) { // This specific case is already handles before
                    dependencyRecords.add(
                        WebdavImportDependencyRecord(
                            webdavImportData = importDataIds[dataType]!!,
                            dependingOn = importDataIds[dependency]!! // can be called safely because of checkImportRequestTypeDependencies() earlier
                        )
                    )
                }
            }
        }

        if (dependencyRecords.isNotEmpty()) {
            !WebDAVImportDependencyRepo.create(dependencyRecords).orDie()
        }

        noData
    }


    suspend fun importNext(env: JEnv): App<Nothing, DynamicIntervalJobState> =
        coroutineScope {
            comprehension(env) {
                val nextImport =
                    !WebDAVImportDataRepo.getNextImport().orDie()
                        ?: return@comprehension KIO.ok(DynamicIntervalJobState.Empty)

                val exit = !importNextData(env, nextImport).transact().run()

                val (jobState, errorMsg) = exit.fold(
                    onError = { cause ->
                        cause.fold(
                            onExpected = { e ->
                                when (e) {
                                    WebDAVError.TypeNotSupported -> DynamicIntervalJobState.Processed to "The documentType WebDAVExportType is not supported as an import."
                                    is WebDAVError.EmailExistingWithOtherId -> {
                                        val message =
                                            "The user mail(s) ${e.emails.joinToString(", ")} already exist(s) in the system with a different id."

                                        DynamicIntervalJobState.Processed to message
                                    }

                                    is WebDAVError.UnknownPrivilege -> {
                                        val message =
                                            "The privilege(s) ${e.privileges.joinToString(", ")} is/are unknown by the system."

                                        DynamicIntervalJobState.Processed to message
                                    }

                                    is WebDAVError.InsertFailed -> DynamicIntervalJobState.Processed to "Insert failed for table ${e.table}: ${e.errorMsg}"
                                    is WebDAVError.JsonToExportParsingFailed -> DynamicIntervalJobState.Processed to "Failed to parse the json file to the export type ${e.className}: ${e.errorMsg}"
                                    is WebDAVError.UnableToRetrieveFile -> DynamicIntervalJobState.Processed to "Failed to retrieve the file of import ${e.importId} from the WebDAV server: ${e.errorMsg}"
                                    is WebDAVError.EntityAlreadyExists -> DynamicIntervalJobState.Processed to "The referred entity with id ${e.entityId} already exists in the system."

                                    is WebDAVError.Unexpected -> DynamicIntervalJobState.Processed to "Unexpected error for import. ${e.errorMsg}"
                                    WebDAVError.ConfigIncomplete -> DynamicIntervalJobState.Fatal("WebDAV config incomplete") to "WebDAV config is incomplete."
                                    WebDAVError.ConfigUnparsable -> DynamicIntervalJobState.Fatal("WebDAV config could not be parsed") to "WebDAV config could not be parsed."
                                    is WebDAVError.CannotMakeFolder -> DynamicIntervalJobState.Processed to "Unable to create folder. This error should not be reachable since it should only be called on initialization."
                                }
                            },
                            onPanic = { e ->
                                DynamicIntervalJobState.Processed to "Unexpected error importing the data into the database: ${e.stackTraceToString()}"
                            })
                    },
                    onSuccess = { DynamicIntervalJobState.Processed to null })
                if (errorMsg != null) !setNextImportError(nextImport, errorMsg)

                KIO.ok(jobState)
            }
        }

    // Todo: If there is an error that will definitely stay - set an error to the other files to reduce load on server
    private suspend fun importNextData(
        env: JEnv,
        nextImport: WebdavImportDataRecord
    ): App<WebDAVError.WebDAVImportNextError, Unit> =
        coroutineScope {
            comprehension(env) {
                val config = !accessConfig()
                if (config.webDAV == null) {
                    return@comprehension KIO.fail(WebDAVError.ConfigIncomplete)
                }

                val client = HttpClient(CIO)

                // Get json file from webdav server at the address of nextImport
                val authHeader = WebDAVService.buildBasicAuthHeader(config.webDAV)
                val url = WebDAVService.getUrl(
                    webDAVConfig = config.webDAV,
                    pathSegments = nextImport.path
                )

                val response = client.get(url) {
                    header("Authorization", authHeader)
                }

                val content = response.bodyAsText()

                !KIO.failOn(!response.status.isSuccess()) {
                    client.close()
                    WebDAVError.UnableToRetrieveFile(
                        importId = nextImport.id,
                        errorMsg = "HTTP ${response.status.value}: $content"
                    )
                }

                client.close()

                fun <C> parseJsonData(
                    content: String,
                    dataClass: Class<C>
                ): KIO<JEnv, WebDAVError.WebDAVImportNextError, C> = KIO.effect {
                    jsonMapper.readValue(content, dataClass)
                }.mapError { ex ->
                    WebDAVError.JsonToExportParsingFailed(
                        className = dataClass.name,
                        errorMsg = "Parsing to export class failed. Json value: $content ; Exception: ${ex.message}"
                    )
                }

                val nextImportDocType = WebDAVExportType.valueOf(nextImport.documentType)

                // Process the data based on the document type
                when (nextImportDocType) {
                    WebDAVExportType.DB_USERS -> {
                        val importData = !parseJsonData(content, DataUsersExport::class.java)
                        !DataUsersExport.importData(importData)
                    }

                    WebDAVExportType.DB_PARTICIPANTS -> {
                        val importData = !parseJsonData(content, DataParticipantsExport::class.java)
                        !DataParticipantsExport.importData(importData)
                    }

                    WebDAVExportType.DB_BANK_ACCOUNTS -> {
                        val importData = !parseJsonData(content, DataBankAccountsExport::class.java)
                        !DataBankAccountsExport.importData(importData)
                    }

                    WebDAVExportType.DB_CONTACT_INFORMATION -> {
                        val importData = !parseJsonData(content, DataContactInformationExport::class.java)
                        !DataContactInformationExport.importData(importData)
                    }

                    WebDAVExportType.DB_EMAIL_INDIVIDUAL_TEMPLATES -> {
                        val importData = !parseJsonData(content, DataEmailIndividualTemplatesExport::class.java)
                        !DataEmailIndividualTemplatesExport.importData(importData)
                    }

                    WebDAVExportType.DB_EVENT_DOCUMENT_TYPES -> {
                        val importData = !parseJsonData(content, DataEventDocumentTypesExport::class.java)
                        !DataEventDocumentTypesExport.importData(importData)
                    }

                    WebDAVExportType.DB_MATCH_RESULT_IMPORT_CONFIGS -> {
                        val importData = !parseJsonData(content, DataMatchResultImportConfigsExport::class.java)
                        !DataMatchResultImportConfigsExport.importData(importData)
                    }

                    WebDAVExportType.DB_STARTLIST_EXPORT_CONFIGS -> {
                        val importData = !parseJsonData(content, DataStartlistExportConfigsExport::class.java)
                        !DataStartlistExportConfigsExport.importData(importData)
                    }

                    WebDAVExportType.DB_WORK_TYPES -> {
                        val importData = !parseJsonData(content, DataWorkTypesExport::class.java)
                        !DataWorkTypesExport.importData(importData)
                    }

                    WebDAVExportType.DB_PARTICIPANT_REQUIREMENTS -> {
                        val importData = !parseJsonData(content, DataParticipantRequirementsExport::class.java)
                        !DataParticipantRequirementsExport.importData(importData)
                    }

                    WebDAVExportType.DB_RATING_CATEGORIES -> {
                        val importData = !parseJsonData(content, DataRatingCategoriesExport::class.java)
                        !DataRatingCategoriesExport.importData(importData)
                    }

                    WebDAVExportType.DB_COMPETITION_CATEGORIES -> {
                        val importData = !parseJsonData(content, DataCompetitionCategoriesExport::class.java)
                        !DataCompetitionCategoriesExport.importData(importData)
                    }

                    WebDAVExportType.DB_FEES -> {
                        val importData = !parseJsonData(content, DataFeesExport::class.java)
                        !DataFeesExport.importData(importData)
                    }

                    WebDAVExportType.DB_NAMED_PARTICIPANTS -> {
                        val importData = !parseJsonData(content, DataNamedParticipantsExport::class.java)
                        !DataNamedParticipantsExport.importData(importData)
                    }

                    WebDAVExportType.DB_COMPETITION_SETUP_TEMPLATES -> {
                        val importData = !parseJsonData(content, DataCompetitionSetupTemplatesExport::class.java)
                        !DataCompetitionSetupTemplatesExport.importData(importData)
                    }

                    WebDAVExportType.DB_COMPETITION_TEMPLATES -> {
                        val importData = !parseJsonData(content, DataCompetitionTemplatesExport::class.java)
                        !DataCompetitionTemplatesExport.importData(importData)
                    }

                    WebDAVExportType.DB_EVENT -> {
                        val importData = !parseJsonData(content, DataEventExport::class.java)
                        !DataEventExport.importData(importData)
                    }

                    WebDAVExportType.DB_COMPETITION -> {
                        val importData = !parseJsonData(content, DataCompetitionExport::class.java)
                        !DataCompetitionExport.importData(importData)
                    }

                    else -> {
                        return@comprehension KIO.fail(WebDAVError.TypeNotSupported)
                    }
                }

                // Mark import as successful
                !WebDAVImportDataRepo.update(nextImport) {
                    importedAt = LocalDateTime.now()
                }.orDie()

                unit
            }
        }

    // Similar function in WebDAVExportService
    private fun setNextImportError(
        dataImport: WebdavImportDataRecord,
        errorMsg: String = "Unexpected error importing the data into the database."
    ) = KIO.comprehension {
        !WebDAVImportDataRepo.update(dataImport) {
            error = errorMsg
            errorAt = LocalDateTime.now()
        }.orDie()
        setErrorOnDependentDataImports(dataImport.id, errorMsg)
    }

    private fun setErrorOnDependentDataImports(
        failedImportId: UUID,
        errorMessage: String
    ): App<Nothing, Unit> = WebDAVImportDataRepo.updateByDependingOnId(failedImportId) {
        errorAt = LocalDateTime.now()
        error = "Dependency with id $failedImportId failed: $errorMessage"
    }.orDie().map { records ->
        records.traverse { setErrorOnDependentDataImports(it.id, errorMessage) }
    }

    fun getImportStatus(): App<Nothing, ApiResponse.ListDto<WebDAVImportStatusDto>> = KIO.comprehension {

        val records = !WebDAVImportProcessRepo.all().orDie()

        records.traverse { record ->

            val imported = record.imports!!.filter { it!!.importedAt != null }.size
            val importsWithError = record.imports!!.filter { it!!.errorAt != null }.size

            record.toDto(
                imported = imported,
                importsWithError = importsWithError
            )
        }.map { ApiResponse.ListDto(it) }
    }
}