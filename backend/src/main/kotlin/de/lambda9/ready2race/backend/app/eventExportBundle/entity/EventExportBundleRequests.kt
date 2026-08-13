package de.lambda9.ready2race.backend.app.eventExportBundle.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

/** Nimmt ein Veranstaltungs-Dokument hinten in die Mappe auf. */
data class AddEventExportBundleItemRequest(
    val document: UUID,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = AddEventExportBundleItemRequest(document = UUID.randomUUID())
    }
}

/**
 * Die neue Reihenfolge, vollständig - dasselbe Prinzip wie bei den Kürzungsregeln
 * (ClubNameRuleOrderRequest): eine Umsortierung ist keine Änderung an einer einzelnen Zeile,
 * zwei nacheinander geschriebene Zeilen könnten dazwischen eine Reihenfolge ergeben, die es
 * nie geben sollte.
 */
data class EventExportBundleOrderRequest(
    val itemIds: List<UUID>,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = EventExportBundleOrderRequest(itemIds = listOf(UUID.randomUUID()))
    }
}
