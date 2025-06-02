package de.lambda9.ready2race.backend.app.contactInformation.control

import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.ContactInformation
import de.lambda9.ready2race.backend.database.generated.tables.records.ContactInformationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CONTACT_INFORMATION
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object ContactInformationRepo {

    private fun ContactInformation.searchFields() = listOf(NAME)

    fun exists(id: UUID) = CONTACT_INFORMATION.exists { ID.eq(id) }

    fun get(id: UUID) = CONTACT_INFORMATION.selectOne { ID.eq(id) }

    fun create(record: ContactInformationRecord) = CONTACT_INFORMATION.insertReturning(record) { ID }

    fun count(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(CONTACT_INFORMATION) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<ContactInformationSort>,
    ): JIO<List<ContactInformationRecord>> = Jooq.query {
        with(CONTACT_INFORMATION) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun delete(id: UUID) = CONTACT_INFORMATION.delete { ID.eq(id) }

    fun update(id: UUID, f: ContactInformationRecord.() -> Unit) = CONTACT_INFORMATION.update(f) { ID.eq(id) }

}