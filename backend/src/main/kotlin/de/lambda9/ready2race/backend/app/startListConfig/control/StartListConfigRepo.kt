package de.lambda9.ready2race.backend.app.startListConfig.control

import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.StartlistExportConfig
import de.lambda9.ready2race.backend.database.generated.tables.records.StartlistExportConfigRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.STARTLIST_EXPORT_CONFIG
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object StartListConfigRepo {

    private fun StartlistExportConfig.searchFields() = listOf(NAME)

    fun get(id: UUID) = STARTLIST_EXPORT_CONFIG.selectOne { ID.eq(id) }

    fun count(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(STARTLIST_EXPORT_CONFIG) {
            fetchCount(
                this,
                search.metaSearch(searchFields())
            )
        }
    }

    fun page(
        params: PaginationParameters<StartListConfigSort>,
    ): JIO<List<StartlistExportConfigRecord>> = Jooq.query {
        with(STARTLIST_EXPORT_CONFIG) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun create(record: StartlistExportConfigRecord) = STARTLIST_EXPORT_CONFIG.insertReturning(record) { ID }

    fun update(id: UUID, f: StartlistExportConfigRecord.() -> Unit) = STARTLIST_EXPORT_CONFIG.update(f) { ID.eq(id) }

    fun delete(id: UUID) = STARTLIST_EXPORT_CONFIG.delete { ID.eq(id) }
}