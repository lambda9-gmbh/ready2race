package de.lambda9.ready2race.backend.app.webDAV.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.config.Config
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import io.ktor.http.*
import java.util.*

object WebDAVService {

    fun getUrl(
        webDAVConfig: Config.WebDAV,
        pathSegments: String
    ): String {
        return URLBuilder(
            protocol = URLProtocol.createOrDefault(webDAVConfig.urlScheme),
            host = webDAVConfig.host,
            pathSegments = listOf(
                webDAVConfig.path,
                "remote.php",
                "dav",
                "files",
                webDAVConfig.authUser,
            ) + pathSegments.split("/").filter { it.isNotEmpty() },
        ).buildString()
    }

    fun buildBasicAuthHeader(webDAVConfig: Config.WebDAV): String {
        val credentials = "${webDAVConfig.authUser}:${webDAVConfig.authPassword}"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
        return "Basic $encoded"
    }


    val webDAVExportTypeDependencies = mapOf(
        WebDAVExportType.DB_PARTICIPANTS to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_BANK_ACCOUNTS to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_CONTACT_INFORMATION to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_EMAIL_INDIVIDUAL_TEMPLATES to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_EVENT_DOCUMENT_TYPES to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_MATCH_RESULT_IMPORT_CONFIGS to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_STARTLIST_EXPORT_CONFIGS to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_WORK_TYPES to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_PARTICIPANT_REQUIREMENTS to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_RATING_CATEGORIES to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_COMPETITION_CATEGORIES to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_FEES to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_NAMED_PARTICIPANTS to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_COMPETITION_SETUP_TEMPLATES to listOf(WebDAVExportType.DB_USERS),
        WebDAVExportType.DB_COMPETITION_TEMPLATES to listOf(WebDAVExportType.DB_USERS),

        WebDAVExportType.DB_EVENT to listOf(
            WebDAVExportType.DB_USERS,
            WebDAVExportType.DB_CONTACT_INFORMATION,
            WebDAVExportType.DB_BANK_ACCOUNTS,
            WebDAVExportType.DB_PARTICIPANT_REQUIREMENTS,
        ),

        WebDAVExportType.DB_COMPETITION to listOf(
            WebDAVExportType.DB_USERS,
            WebDAVExportType.DB_EVENT,
            WebDAVExportType.DB_COMPETITION_CATEGORIES,
            WebDAVExportType.DB_FEES,
            WebDAVExportType.DB_NAMED_PARTICIPANTS
        )
    )

    // todo refactor this KIO to traverse
    fun checkRequestTypeDependencies(types: List<WebDAVExportType>): App<WebDAVError.MissingDependency, Unit> =
        KIO.comprehension {
            types.forEach { exportType ->
                webDAVExportTypeDependencies[exportType]?.forEach { requiredDependency ->
                    !KIO.failOn(!types.contains(requiredDependency)) {
                        WebDAVError.MissingDependency(
                            exportType,
                            requiredDependency
                        )
                    }
                }
            }
            unit
        }

    private val sortedDbExportTypes: List<WebDAVExportType> by lazy {
        val dbTypes = WebDAVExportType.entries.filter { it.name.startsWith("DB_") }
        val sorted = mutableListOf<WebDAVExportType>()
        val visited = mutableSetOf<WebDAVExportType>()
        val visiting = mutableSetOf<WebDAVExportType>()

        fun visit(type: WebDAVExportType) {
            if (type in visited) {
                return
            }

            visiting.add(type)
            webDAVExportTypeDependencies[type]?.forEach { dependency ->
                visit(dependency)
            }
            visiting.remove(type)
            visited.add(type)
            sorted.add(type)
        }

        dbTypes.forEach { type ->
            if (type !in visited) {
                visit(type)
            }
        }

        sorted.toList()
    }

    fun sortDbExportTypes(types: List<WebDAVExportType>): List<WebDAVExportType> = sortedDbExportTypes
        .filter { it in types }

    fun getWebDavDataJsonFileName(type: WebDAVExportType): String {
        return (when (type) {
            WebDAVExportType.DB_USERS -> "users"
            WebDAVExportType.DB_PARTICIPANTS -> "participants"
            WebDAVExportType.DB_BANK_ACCOUNTS -> "bank_accounts"
            WebDAVExportType.DB_CONTACT_INFORMATION -> "contact_information"
            WebDAVExportType.DB_EMAIL_INDIVIDUAL_TEMPLATES -> "email_individual_templates"
            WebDAVExportType.DB_EVENT_DOCUMENT_TYPES -> "event_document_types"
            WebDAVExportType.DB_MATCH_RESULT_IMPORT_CONFIGS -> "match_result_import_configs"
            WebDAVExportType.DB_STARTLIST_EXPORT_CONFIGS -> "startlist_export_configs"
            WebDAVExportType.DB_WORK_TYPES -> "work_types"
            WebDAVExportType.DB_PARTICIPANT_REQUIREMENTS -> "participant_requirements"
            WebDAVExportType.DB_RATING_CATEGORIES -> "rating_categories"
            WebDAVExportType.DB_COMPETITION_CATEGORIES -> "competition_categories"
            WebDAVExportType.DB_FEES -> "fees"
            WebDAVExportType.DB_NAMED_PARTICIPANTS -> "named_participants"
            WebDAVExportType.DB_COMPETITION_SETUP_TEMPLATES -> "competition_setup_templates"
            WebDAVExportType.DB_COMPETITION_TEMPLATES -> "competition_templates"
            WebDAVExportType.DB_EVENT -> "event"
            WebDAVExportType.DB_COMPETITION -> "competition"
            else -> ""
        }) + ".json"
    }


}