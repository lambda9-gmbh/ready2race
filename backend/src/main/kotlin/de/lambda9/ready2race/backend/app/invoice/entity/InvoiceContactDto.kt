package de.lambda9.ready2race.backend.app.invoice.entity

// Ein Empfaenger der Rechnung, so wie er gerade im Verein hinterlegt ist. Bewusst der aktuelle
// Stand und nicht der zur Rechnungserstellung: die Anzeige dient dazu, jemanden zu erreichen.
data class InvoiceContactDto(
    val name: String,
    val email: String,
)
