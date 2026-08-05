package de.lambda9.ready2race.backend.app.competitionExecution.entity

/**
 * In welchem Format eine Startliste ausgegeben wird. Das CSV-Spalten-Preset steckt nicht mehr hier: es
 * wird aus der Zeitnahme-Konfiguration des Wettkampfs und der Runde aufgeloest
 * (siehe [StartListConfigTarget]).
 */
enum class StartListFileType { PDF, CSV }
