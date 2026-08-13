/**
 * Die Reiter der öffentlichen Ergebnisseite und ihre Ansteuerung über die URL (?tab=…) — für
 * QR-Aushänge, die direkt auf einem bestimmten Reiter landen sollen (z. B.
 * /results/event/{id}?tab=live am Steg). Die URL-Werte sind sprechend ('results', 'live',
 * 'my-event') statt der internen Reiternamen; 'results' ist das explizite Default-Ziel und
 * verhält sich wie eine URL ganz ohne Parameter.
 *
 * Challenge-Events haben einen eigenen Reitersatz (siehe CHALLENGE_RESULTS_TABS in der
 * ResultsPage) — dort greift diese Auflösung gar nicht, ein tab-Parameter läuft wie bisher ins
 * Leere und die Seite startet auf ihrem Default.
 */

export const RESULTS_TABS = ['latest-results', 'live', 'my-event'] as const
export type ResultsTab = (typeof RESULTS_TABS)[number]

/** Die erlaubten Werte des tab-Suchparameters. */
export const RESULTS_TAB_SEARCH_VALUES = ['results', 'live', 'my-event'] as const
export type ResultsTabSearchValue = (typeof RESULTS_TAB_SEARCH_VALUES)[number]

/**
 * Für validateSearch der Route: nur bekannte Werte überleben, alles andere wird zu undefined
 * und landet damit auf dem Default — Tippfehler in einem gedruckten QR-Code sollen keine
 * kaputte Seite ergeben.
 */
export const parseResultsTabSearch = (
    value: string | undefined,
): ResultsTabSearchValue | undefined =>
    (RESULTS_TAB_SEARCH_VALUES as readonly string[]).includes(value ?? '')
        ? (value as ResultsTabSearchValue)
        : undefined

/** Der Reiter, mit dem die Seite startet — dasselbe Muster wie der bisherige my-event-Einstieg. */
export const initialResultsTab = (value: ResultsTabSearchValue | undefined): ResultsTab =>
    value === 'live' ? 'live' : value === 'my-event' ? 'my-event' : 'latest-results'
