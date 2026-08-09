package de.lambda9.ready2race.backend.app.certificate.entity

import java.util.UUID

enum class AwardCertificateMode {
    PER_ATHLETE,
    PER_TEAM,
}

data class AwardCertificateOptions(
    val maxPlace: Int,
    val mode: AwardCertificateMode,
    val withBackground: Boolean,
    /**
     * Ob die Wertungskategorie auf der Urkunde erscheint. Standardmäßig aus: ohne diese Option ist
     * die Ausgabe byte-gleich zu der vor dem 09.08.2026. Gedruckt wird sie über den Platzhalter
     * [de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType.RATING_CATEGORY],
     * den die Vorlage dafür einmal gesetzt bekommen muss.
     *
     * Der *Platz* auf der Urkunde bleibt davon unberührt: er ist weiterhin der wettkampfweite und
     * nicht der Platz innerhalb der Kategorie. Das steht bewusst quer zur Platzierungsansicht —
     * eine Urkunde hängt jahrelang im Bootshaus neben älteren, auf denen „3. Platz" den Platz im
     * Rennen meinte.
     */
    val printRatingCategory: Boolean,
) {
    companion object {
        const val DEFAULT_MAX_PLACE = 3
    }
}

data class AwardCertificateParticipant(
    val firstName: String,
    val lastName: String,
    val role: String,
)

/** Ein platziertes Boot, aufbereitet aus der Platzierungsberechnung. */
data class AwardCertificateTeam(
    val place: Int,
    val clubName: String,
    val teamName: String?,
    val result: String?,
    val startNumber: Int,
    /** Der Name der Wertungskategorie, null ohne Zuordnung. */
    val ratingCategory: String?,
    /** abgemeldet, ausgeschieden oder disqualifiziert */
    val excluded: Boolean,
    val participants: List<AwardCertificateParticipant>,
    val registrationId: UUID,
)

/**
 * Eine einzelne Urkunde, also genau eine Seite. [firstName] und [lastName] sind nur im Modus
 * [AwardCertificateMode.PER_ATHLETE] gesetzt, da im [AwardCertificateMode.PER_TEAM]-Modus mehrere
 * Personen dieselbe Urkunde teilen und [names] dafür bereits die vollständige, zeilenweise Liste ist.
 */
data class AwardCertificateEntry(
    val place: Int,
    val competitionIdentifier: String,
    val competitionName: String,
    val competitionShortName: String?,
    val clubName: String,
    val teamName: String?,
    val result: String?,
    val ratingCategory: String?,
    val names: List<String>,
    val firstName: String?,
    val lastName: String?,
    val registrationId: UUID,
)
