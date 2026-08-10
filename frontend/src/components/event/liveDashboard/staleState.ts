/**
 * Wie die Anzeige mit einem nicht mehr frischen Stand umgeht.
 *
 * `fromCache` unterscheidet „Abruf gerade eben fehlgeschlagen" von „das hier ist der Stand von
 * vorhin": Ab einer halben Minute Abstand nennt das Banner Datum und Uhrzeit, statt nur eine
 * gestörte Verbindung zu melden.
 */

/** Ab hier gilt ein Stand als von früher, nicht als eben verpasste Aktualisierung. */
const FROM_CACHE_AFTER_MS = 30_000

export type StaleState = {
    show: boolean
    fromCache: boolean
    actionsLocked: boolean
}

export const describeStale = (
    fetchedAt: number | null,
    stale: boolean,
    now: number,
): StaleState => {
    if (!stale) {
        return {show: false, fromCache: false, actionsLocked: false}
    }
    const fromCache = fetchedAt !== null && now - fetchedAt >= FROM_CACHE_AFTER_MS
    return {show: true, fromCache, actionsLocked: true}
}
