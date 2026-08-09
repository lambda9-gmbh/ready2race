/**
 * Der automatische Datenabgleich der Durchführungsseite — die Entscheidungen, die sich ohne
 * Rendering prüfen lassen: ob überhaupt abgeglichen wird, in welchem Takt, und was die Seite über
 * die Verbindung sagt.
 *
 * Die Einstellung kommt zentral von der Veranstaltung (siehe `ExecutionAutoRefresh` im Backend).
 * Sie hier trotzdem noch einmal zu begrenzen ist keine doppelte Arbeit: Die Grenzen wanderten mit
 * einer Migration in die Datenbank, und ein Bestand von vor dieser Änderung — oder ein Prod-Abzug,
 * ein Seed — kann eine 2 oder eine 3600 tragen. Ein Takt von 2 Sekunden, den niemand eingestellt
 * hat, fiele erst am Renntag auf.
 */

/** Schneller als das Tippen eines Ergebnisses; darunter kostet der Takt nur Abfragen. */
export const AUTO_REFRESH_MIN_SECONDS = 5
/** Darüber ist es kein Abgleich mehr, sondern gelegentliches Nachschauen. */
export const AUTO_REFRESH_MAX_SECONDS = 60
export const AUTO_REFRESH_DEFAULT_SECONDS = 5

export type AutoRefreshConfig = {
    enabled: boolean
    seconds: number
}

/**
 * Der Takt, den die Seite tatsächlich fährt. Krumme Werte werden auf die Grenze gezogen statt
 * abgelehnt: Die Alternative wäre eine Seite, die wegen einer unplausiblen Zahl gar nicht mehr
 * nachzieht — der schlechtere von beiden Fehlern.
 */
export const clampRefreshSeconds = (seconds: number | null | undefined): number => {
    if (seconds == null || !Number.isFinite(seconds)) {
        return AUTO_REFRESH_DEFAULT_SECONDS
    }

    return Math.min(AUTO_REFRESH_MAX_SECONDS, Math.max(AUTO_REFRESH_MIN_SECONDS, Math.round(seconds)))
}

/**
 * Der Takt in Millisekunden, oder `undefined` für „nicht abgleichen".
 *
 * `undefined` ist hier die Aussage, nicht 0 oder Infinity: Genau das erwartet `useFetch` als
 * `autoReloadInterval`, um keinen Wecker zu stellen. Ausgeschaltet heißt ausgeschaltet — kein
 * Timer, kein Request.
 *
 * [paused] hält den Takt an, ohne ihn abzuschalten: Solange ein Dialog offen ist, würde ein
 * Abgleich unter der Hand die Laufauswahl verschieben, auf der die Eingabe gerade steht.
 */
export const refreshIntervalMs = (
    config: AutoRefreshConfig,
    paused: boolean = false,
): number | undefined =>
    config.enabled && !paused ? clampRefreshSeconds(config.seconds) * 1000 : undefined

export type SyncStatus = 'ok' | 'stale' | 'error'

/**
 * Was die Seite über den Abgleich sagt.
 *
 * `stale` ist der Fall, für den das hier gebaut ist: Die Verbindung ist weg, aber der zuletzt
 * erfolgreich geladene Stand steht noch auf dem Schirm. Der bleibt stehen, und daneben steht ein
 * Hinweis — eine leere Seite wäre am Steg schlimmer als eine, die eine Minute alt ist und das sagt.
 *
 * `error` gibt es nur, wenn nie etwas ankam: Dann gibt es nichts zu halten, und der Hinweis muss
 * deutlicher ausfallen.
 */
export const syncStatus = ({
    failures,
    hasData,
}: {
    failures: number
    hasData: boolean
}): SyncStatus => {
    if (failures === 0) {
        return 'ok'
    }

    return hasData ? 'stale' : 'error'
}
