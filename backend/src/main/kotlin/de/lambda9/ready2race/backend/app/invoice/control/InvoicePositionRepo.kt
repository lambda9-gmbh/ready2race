package de.lambda9.ready2race.backend.app.invoice.control

import de.lambda9.ready2race.backend.database.generated.tables.records.InvoicePositionRecord
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object InvoicePositionRepo {

    fun create(
        records: List<InvoicePositionRecord>
    ): JIO<Int> = Jooq.query {
        batchInsert(records)
            .execute()
            .sum()
    }


}