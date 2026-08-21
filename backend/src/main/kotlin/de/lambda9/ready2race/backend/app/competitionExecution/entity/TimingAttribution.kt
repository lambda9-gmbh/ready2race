package de.lambda9.ready2race.backend.app.competitionExecution.entity

/**
 * Die Nennung der externen Zeitnahme, die auf den öffentlichen Ergebnissen eines Laufs steht.
 *
 * Anbieter verlangen sie in ihren Nutzungsbedingungen (RaceClocker Nr. 6): Wer ihre Zeiten
 * veröffentlicht, nennt sie sichtbar und verlinkt sie. Die Nennung wird beim Schreiben der
 * Ergebnisse am Lauf abgelegt (Abzug, kein Verweis) - siehe V202608201200.
 */
data class TimingAttribution(
    val name: String,
    val url: String?,
) {
    companion object {

        const val RACECLOCKER_NAME = "RaceClocker"

        /** Der Rückfall, wenn dem Wettkampf kein Rennen zugeordnet ist - dann gibt es keine Seite. */
        const val RACECLOCKER_HOME = "https://raceclocker.com"

        /**
         * Der Live-Abruf holt seine Ergebnisse direkt aus RaceClocker und kennt keine
         * Import-Konfiguration, an der eine Nennung gepflegt wäre. Verlinkt wird die
         * Ergebnisseite des Rennens, aus dem die Zeiten kommen - dieselbe Adresse, die der Abruf
         * anfragt. Ein Verweis auf die Startseite ließe den Leser mit der Suche allein, und die
         * Nennung soll die Zeiten belegen und nicht nur den Anbieter nennen.
         */
        fun raceClocker(resultsUrl: String?) = TimingAttribution(
            name = RACECLOCKER_NAME,
            url = resultsUrl?.takeIf { it.isNotBlank() } ?: RACECLOCKER_HOME,
        )

        /**
         * Die Nennung einer Import-Konfiguration - `null`, solange dort keine gepflegt ist.
         */
        fun of(name: String?, url: String?): TimingAttribution? =
            name?.takeIf { it.isNotBlank() }?.let { TimingAttribution(it, url?.takeIf { u -> u.isNotBlank() }) }
    }
}
