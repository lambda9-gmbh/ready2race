package de.lambda9.ready2race.backend.app.caterer.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.caterer.control.CatererRepo
import de.lambda9.ready2race.backend.app.caterer.control.toRecord
import de.lambda9.ready2race.backend.app.caterer.entity.CatererError
import de.lambda9.ready2race.backend.app.caterer.entity.NewCatererTransactionDTO
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.math.BigDecimal
import java.util.UUID

object CatererService {

    fun createCateringTransaction(
        transaction: NewCatererTransactionDTO,
        catererId: UUID
    ): App<CatererError, ApiResponse.NoData> = KIO.comprehension {

        // Validate that the user exists
        !AppUserRepo.exists(transaction.appUserId).orDie()
            .onFalseFail { CatererError.UserNotFound }

        // Validate price is non-negative if provided
        !(transaction.price?.let { price ->
            if (price < BigDecimal.ZERO) {
                KIO.fail(CatererError.InvalidPrice)
            } else {
                KIO.ok(Unit)
            }
        } ?: KIO.ok(Unit))

        // Create the transaction record
        val record = !transaction.toRecord(catererId)
        !CatererRepo.create(record).orDie()

        noData
    }
}