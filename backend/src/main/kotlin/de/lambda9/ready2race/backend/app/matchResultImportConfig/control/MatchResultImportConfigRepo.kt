package de.lambda9.ready2race.backend.app.matchResultImportConfig.control

import de.lambda9.ready2race.backend.app.matchResultImportConfig.entity.MatchResultImportConfigSort
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.MatchResultImportConfig
import de.lambda9.ready2race.backend.database.generated.tables.StartlistExportConfig
import de.lambda9.ready2race.backend.database.generated.tables.records.MatchResultImportConfigRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.StartlistExportConfigRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.MATCH_RESULT_IMPORT_CONFIG
import de.lambda9.ready2race.backend.database.generated.tables.references.STARTLIST_EXPORT_CONFIG
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object MatchResultImportConfigRepo {

    private fun MatchResultImportConfig.searchFields() = listOf(NAME)

    fun get(id: UUID) = MATCH_RESULT_IMPORT_CONFIG.selectOne { ID.eq(id) }

    fun count(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(MATCH_RESULT_IMPORT_CONFIG) {
            fetchCount(
                this,
                search.metaSearch(searchFields())
            )
        }
    }

    fun page(
        params: PaginationParameters<MatchResultImportConfigSort>,
    ): JIO<List<MatchResultImportConfigRecord>> = Jooq.query {
        with(MATCH_RESULT_IMPORT_CONFIG) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun create(record: MatchResultImportConfigRecord) = MATCH_RESULT_IMPORT_CONFIG.insertReturning(record) { ID }

    fun update(id: UUID, f: MatchResultImportConfigRecord.() -> Unit) = MATCH_RESULT_IMPORT_CONFIG.update(f) { ID.eq(id) }

    fun delete(id: UUID) = MATCH_RESULT_IMPORT_CONFIG.delete { ID.eq(id) }
}