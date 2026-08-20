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

        /**
         * Der Live-Abruf holt seine Ergebnisse direkt aus RaceClocker und kennt keine
         * Import-Konfiguration, an der eine Nennung gepflegt wäre. Für ihn steht sie hier.
         */
        val RACECLOCKER = TimingAttribution("RaceClocker", "https://raceclocker.com")

        /**
         * Die Nennung einer Import-Konfiguration - `null`, solange dort keine gepflegt ist.
         */
        fun of(name: String?, url: String?): TimingAttribution? =
            name?.takeIf { it.isNotBlank() }?.let { TimingAttribution(it, url?.takeIf { u -> u.isNotBlank() }) }
    }
}
