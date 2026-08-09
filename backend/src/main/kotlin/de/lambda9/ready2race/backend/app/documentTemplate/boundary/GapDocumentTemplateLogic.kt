package de.lambda9.ready2race.backend.app.documentTemplate.boundary

import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType

object GapDocumentTemplateLogic {

    /**
     * Der Serien-Renderer zeichnet je Urkunde genau eine Seite und berücksichtigt nur Platzhalter
     * mit `page == 1`. Eine Siegerurkunde ist per Definition einseitig, deshalb wird eine Anfrage mit
     * Platzhaltern auf einer höheren Seite abgelehnt. Die Teilnahmeurkunde bleibt unangetastet, weil
     * ihre Vorlage mehrseitig sein darf.
     */
    fun placeholdersFitOnSinglePage(
        type: GapDocumentType,
        placeholders: List<GapDocumentPlaceholderRequest>,
    ): Boolean = type != GapDocumentType.AWARD_CERTIFICATE || placeholders.all { it.page == 1 }

    /**
     * `GapDocumentType.allowedPlaceholders` sagt dem Editor, welche Platzhaltertypen zu einem
     * Dokumenttyp passen - das ist aber nur eine Hilfe im Frontend. Ein fehlerhafter oder älterer
     * Client könnte trotzdem einen nicht passenden Typ mitschicken, zum Beispiel einen
     * PLACE-Platzhalter auf einer Teilnahmeurkunde, der beim Druck als leere Box erscheinen würde.
     * Deshalb wird das hier serverseitig geprüft.
     */
    fun placeholderTypesAreAllowed(
        type: GapDocumentType,
        placeholders: List<GapDocumentPlaceholderRequest>,
    ): Boolean = placeholders.all { it.type in type.allowedPlaceholders }

    /**
     * Eine Vorlage trägt ihren Dokumenttyp fest von der Anlage an (der Editor sperrt ihn beim
     * Bearbeiten). Würde man sie trotzdem einem anderen Typ-Slot zuweisen - z. B. eine
     * Teilnahmeurkunden-Vorlage unter `AWARD_CERTIFICATE` -, befüllt die Generierung nur die
     * zufällig überlappenden Platzhalter; die Organisation bekäme leer wirkende Urkunden ohne jede
     * Fehlermeldung. Diese Prüfung spiegelt die Frontend-Sperre serverseitig, für Clients, die sie
     * umgehen (z. B. ein direkter API-Aufruf).
     */
    fun templateTypeMatches(templateType: GapDocumentType, slot: GapDocumentType): Boolean =
        templateType == slot

    /** Grobe Vorprüfung des Font-Uploads anhand der Dateiendung, bevor der Inhalt gelesen wird. */
    fun hasValidFontExtension(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in setOf("ttf", "otf")
}
