package de.lambda9.ready2race.backend.app.club.entity

/**
 * Eine Zeile der Pflegeseite: ein Verein, so oft er im System geschrieben steht.
 *
 * [names] steht bewusst vollständig im Datensatz und nicht nur als Zahl. Die Normalisierung
 * ([de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey]) kann zwei wirklich verschiedene
 * Vereine auf denselben Schlüssel legen; sichtbar wird das nur, wenn die zusammengefassten
 * Schreibweisen untereinander stehen. Ohne diese Liste bliebe der Fehler unbemerkt.
 */
data class ClubShortNameDto(
    val nameKey: String,
    val names: List<String>,
    val shortName: String,
    /** `false` heißt: [shortName] kommt aus der Heuristik und ändert sich, wenn sie sich ändert. */
    val maintained: Boolean,
)
