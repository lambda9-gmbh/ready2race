package de.lambda9.ready2race.backend.app.timingConfig.entity

/**
 * Mit welchem Fremdsystem die Zeitnahme eines Wettkampfs arbeitet.
 *
 * [RACECLOCKER] holt die Ergebnisse aus dem oeffentlichen Ergebnis-Feed und braucht dafuer die beiden
 * Rennen-URLs. [WEBSCORER] kennt keinen Rueckweg ueber eine URL; dort kommen die Ergebnisse als
 * Tabelle zurueck und hochgeladen. Ist nichts gesetzt (Spalte `null`), ist der Wettkampf an kein
 * Fremdsystem gebunden -- dann fehlt lediglich die Vorbelegung, und der Export verlangt sie.
 *
 * Als Text gespeichert und von Hand konvertiert, wie ChainProgressionMode: es gibt keinen
 * jOOQ-Converter in diesem Projekt.
 */
enum class TimingSystem { RACECLOCKER, WEBSCORER }
