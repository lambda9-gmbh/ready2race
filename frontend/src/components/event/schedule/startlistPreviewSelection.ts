/**
 * Die Auswahl-Logik der Export-Vorschau (Startlisten-Sammelexport, Delta-Modus) als pure
 * Funktionen - der Dialog hält nur das Set der gewählten Lauf-Ids, alles Rechnen steht hier und
 * ist ohne Komponente testbar.
 *
 * Exportierbar ist ein Lauf nur mit geplanter Startzeit: ohne sie blockierte er den Export
 * (STARTLIST_MATCHES_WITHOUT_START_TIME), deshalb nimmt die Vorschau ihn von vornherein aus der
 * Auswahl und weist ihn in einem eigenen Abschnitt aus.
 */

type PreviewRow = {
    matchId: string
    startTime?: string | null
}

export const isExportable = (row: PreviewRow): boolean =>
    row.startTime !== undefined && row.startTime !== null

export const exportableIds = (rows: PreviewRow[]): string[] =>
    rows.filter(isExportable).map(row => row.matchId)

/** Beim Laden der Vorschau: alles Exportierbare vorausgewählt. */
export const initialSelection = (rows: PreviewRow[]): Set<string> => new Set(exportableIds(rows))

export const toggleMatch = (selected: Set<string>, matchId: string): Set<string> => {
    const next = new Set(selected)
    if (next.has(matchId)) {
        next.delete(matchId)
    } else {
        next.add(matchId)
    }
    return next
}

/** Kopfzeilen-Checkbox: alles gewählt → alles abwählen, sonst alles Exportierbare anwählen. */
export const toggleAll = (selected: Set<string>, rows: PreviewRow[]): Set<string> => {
    const ids = exportableIds(rows)
    return selected.size === ids.length ? new Set() : new Set(ids)
}

/**
 * Der matchIds-Query-Parameter des Exports. undefined (Parameter weglassen, kleinere URL) nur,
 * wenn die Auswahl den Plan gar nicht einschränkt: JEDER Lauf der Vorschau ist exportierbar und
 * gewählt. Sobald ein Lauf abgewählt ist ODER ein nicht exportierbarer existiert, wird die
 * Auswahl ausdrücklich mitgeschickt - sonst liefe der Export in genau den Startzeit-Fehler, den
 * die Vorschau vermeiden soll. Serverseitig wirkt der Parameter ohnehin nur als Schnittmenge mit
 * dem Plan.
 */
export const matchIdsParam = (selected: Set<string>, rows: PreviewRow[]): string[] | undefined =>
    rows.length > 0 && rows.every(isExportable) && selected.size === rows.length
        ? undefined
        : [...selected]
