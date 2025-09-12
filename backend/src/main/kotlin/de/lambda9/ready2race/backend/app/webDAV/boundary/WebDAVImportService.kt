package de.lambda9.ready2race.backend.app.webDAV.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.checkRequestTypeDependencies
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.webDAVExportTypeDependencies
import de.lambda9.ready2race.backend.app.webDAV.control.WebDAVImportDataRepo
import de.lambda9.ready2race.backend.app.webDAV.control.WebDAVImportDependencyRepo
import de.lambda9.ready2race.backend.app.webDAV.control.WebDAVImportProcessRepo
import de.lambda9.ready2race.backend.app.webDAV.entity.*
import de.lambda9.ready2race.backend.calls.comprehension.CallComprehensionScope
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavImportDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavImportDependencyRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavImportProcessRecord
import de.lambda9.ready2race.backend.kio.accessConfig
import de.lambda9.ready2race.backend.kio.comprehension
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
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
                    // Skip the root folder (which has the username as displayname)
                    // We want actual subfolders only
                    if (displayName.isNotEmpty() && !responseContent.contains("/dav/files/admin/</d:href>")) {
                        folderNames.add(displayName)
                    }
                }
            }
        }

        return folderNames.distinct()
    }

    suspend fun CallComprehensionScope.getImportOptionTypes(folderName: String): App<WebDAVError.WebDAVExternError, ApiResponse.ListDto<WebDAVExportType>> {
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

        return KIO.ok(ApiResponse.ListDto(manifest.exportedTypes))
    }


    fun initializeImportData(
        request: WebDAVImportRequest, userId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !KIO.failOn(request.selectedData.any { !it.name.startsWith("DB_") }) { WebDAVError.OnlyDataImportsAllowed }
        !checkRequestTypeDependencies(request.selectedData)

        val importProcess = WebdavImportProcessRecord(
            id = UUID.randomUUID(),
            importFolderName = request.folderName,
            createdAt = LocalDateTime.now(),
            createdBy = userId
        )
        val processId = !WebDAVImportProcessRepo.create(importProcess).orDie()


        fun buildImportDataRecord(
            dataType: WebDAVExportType
        ): WebdavImportDataRecord {
            return WebdavImportDataRecord(
                id = UUID.randomUUID(),
                webdavImportProcess = processId,
                documentType = dataType.name,
                path = "${request.folderName}/${WebDAVService.getWebDavDataJsonFileName(dataType)}",
                importedAt = null,
                errorAt = null,
                error = null
            )
        }

        val importDataRecords = mutableListOf<WebdavImportDataRecord>()
        val importDataIds = mutableMapOf<WebDAVExportType, UUID>()

        // Create import data records for each selected type
        request.selectedData.forEach { dataType ->
            val record = buildImportDataRecord(dataType)
            importDataRecords.add(record)
            importDataIds[dataType] = record.id
        }

        // Create import data records
        !WebDAVImportDataRepo.create(importDataRecords).orDie()


        // Create dependencies using the dependency map
        val dependencyRecords = mutableListOf<WebdavImportDependencyRecord>()

        request.selectedData.forEach { dataType ->
            webDAVExportTypeDependencies[dataType]?.forEach { dependency ->
                dependencyRecords.add(
                    WebdavImportDependencyRecord(
                        webdavImportData = importDataIds[dataType]!!,
                        dependingOn = importDataIds[dependency]!! // can be called safely because of checkImportRequestTypeDependencies() earlier
                    )
                )
            }
        }

        if (dependencyRecords.isNotEmpty()) {
            !WebDAVImportDependencyRepo.create(dependencyRecords).orDie()
        }

        noData
    }


    // Todo: If there is an error that will definitely stay - set an error to the other files to reduce load on server
    suspend fun importNext(env: JEnv): App<WebDAVError.WebDAVImportNextError, Unit> =
        coroutineScope {
            comprehension(env) {
                val config = !accessConfig()
                if (config.webDAV == null) {
                    return@comprehension KIO.fail(WebDAVError.ConfigIncomplete)
                }

                val client = HttpClient(CIO)

                val nextImport =
                    !WebDAVImportDataRepo.getNextImport().orDie().onNullFail { WebDAVError.NoFilesToImport }

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

                if (!response.status.isSuccess()) {
                    !setNextImportError(nextImport, "HTTP ${response.status.value}: $content")
                    client.close()
                    return@comprehension KIO.fail(WebDAVError.Unexpected)
                }
                client.close()

                fun <C> parseJsonData(
                    content: String,
                    dataClass: Class<C>
                ): KIO<JEnv, WebDAVError.WebDAVImportNextError, C> = KIO.effect {
                    jsonMapper.readValue(content, dataClass)
                }.mapError { ex ->
                    !setNextImportError(nextImport, "Import failed: ${ex.message ?: ex::class.simpleName}")
                    WebDAVError.Unexpected
                }

                // Process the data based on document type
                when (nextImport.documentType) {
                    WebDAVExportType.DB_USERS.name -> {
                        val importData = !parseJsonData(content, DataUsersExport::class.java)
                        !DataUsersExport.importData(importData).mapError {
                            val msg = when (it) {
                                WebDAVError.EmailExistingWithOtherId -> "An email in the import already exists with another id."
                                else -> "Unexpected error importing the data into the database."
                            }
                            !setNextImportError(nextImport, msg)
                            it
                        }
                    }

                    WebDAVExportType.DB_PARTICIPANTS.name -> {
                        val importData = !parseJsonData(content, DataParticipantsExport::class.java)
                        !DataParticipantsExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    WebDAVExportType.DB_BANK_ACCOUNTS.name -> {
                        val importData = !parseJsonData(content, DataBankAccountsExport::class.java)
                        !DataBankAccountsExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    WebDAVExportType.DB_CONTACT_INFORMATION.name -> {
                        val importData = !parseJsonData(content, DataContactInformationExport::class.java)
                        !DataContactInformationExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    WebDAVExportType.DB_EMAIL_INDIVIDUAL_TEMPLATES.name -> {
                        val importData = !parseJsonData(content, DataEmailIndividualTemplatesExport::class.java)
                        !DataEmailIndividualTemplatesExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    WebDAVExportType.DB_EVENT_DOCUMENT_TYPES.name -> {
                        val importData = !parseJsonData(content, DataEventDocumentTypesExport::class.java)
                        !DataEventDocumentTypesExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    WebDAVExportType.DB_MATCH_RESULT_IMPORT_CONFIGS.name -> {
                        val importData = !parseJsonData(content, DataMatchResultImportConfigsExport::class.java)
                        !DataMatchResultImportConfigsExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    WebDAVExportType.DB_STARTLIST_EXPORT_CONFIGS.name -> {
                        val importData = !parseJsonData(content, DataStartlistExportConfigsExport::class.java)
                        !DataStartlistExportConfigsExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    WebDAVExportType.DB_WORK_TYPES.name -> {
                        val importData = !parseJsonData(content, DataWorkTypesExport::class.java)
                        !DataWorkTypesExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    WebDAVExportType.DB_PARTICIPANT_REQUIREMENTS.name -> {
                        val importData = !parseJsonData(content, DataParticipantRequirementsExport::class.java)
                        !DataParticipantRequirementsExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    WebDAVExportType.DB_RATING_CATEGORIES.name -> {
                        val importData = !parseJsonData(content, DataRatingCategoriesExport::class.java)
                        !DataRatingCategoriesExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    WebDAVExportType.DB_COMPETITION_CATEGORIES.name -> {
                        val importData = !parseJsonData(content, DataCompetitionCategoriesExport::class.java)
                        !DataCompetitionCategoriesExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    WebDAVExportType.DB_FEES.name -> {
                        val importData = !parseJsonData(content, DataFeesExport::class.java)
                        !DataFeesExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    WebDAVExportType.DB_NAMED_PARTICIPANTS.name -> {
                        val importData = !parseJsonData(content, DataNamedParticipantsExport::class.java)
                        !DataNamedParticipantsExport.importData(importData).mapError {
                            !setNextImportError(nextImport, "Unexpected error importing the data into the database.")
                            it
                        }
                    }

                    else -> {
                        !setNextImportError(nextImport, "Unknown import type: ${nextImport.documentType}")
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
    private fun setNextImportError(dataImport: WebdavImportDataRecord, errorMsg: String) = KIO.comprehension {
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
}