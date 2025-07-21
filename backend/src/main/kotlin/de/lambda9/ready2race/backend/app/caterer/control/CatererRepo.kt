package de.lambda9.ready2race.backend.app.caterer.control

import de.lambda9.ready2race.backend.database.generated.tables.references.CATERER_TRANSACTION
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.tailwind.jooq.JIO
import java.util.UUID

object CatererRepo {
    
    fun create(record: de.lambda9.ready2race.backend.database.generated.tables.records.CatererTransactionRecord): JIO<UUID> = 
        CATERER_TRANSACTION.insertReturning(record) { CATERER_TRANSACTION.ID }
}