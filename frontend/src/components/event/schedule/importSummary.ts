import {ImportRowResultDto, ImportRowStatus} from '@api/types.gen.ts'

// Eigene Datei statt common.ts, weil die Einordnung der Vorschau-Zeilen ein abgeschlossenes Thema
// ist (siehe ScheduleImportDialog) und common.ts ohnehin schon der Sammelplatz für alles rund um
// den Zeitplan ist.

/**
 * Zeilen, die der Import weder einem Lauf zuordnen konnte noch bewusst frei lassen sollte.
 *
 * Warum das eine eigene Kategorie braucht: Das Matching ist strikt (siehe ScheduleImport.matchRow),
 * es vergleicht den Lauf-Namen aus der Datei exakt mit dem Wettkampf-Setup. Schrumpft eine Runde,
 * weil weniger Meldungen da sind, heißt der Lauf in der Datei plötzlich „VF2“ und im Setup nur noch
 * „VF1“ - die Zeile bekommt MATCH_NOT_FOUND, wird als freier Slot angelegt und der echte Lauf
 * bleibt still unter „Nicht verplante Läufe“ stehen. Genau das ist am 1. Regattatag passiert und in
 * einer Vorschau mit 40+ Zeilen niemandem aufgefallen.
 *
 * FREE ist bewusst NICHT dabei: eine Zeile ohne Wettkampf-Angabe (Siegerehrung, Pause) ist ein
 * gewollter Programmpunkt, keine Auffälligkeit.
 */
export const isUnmatchedImportRow = (row: ImportRowResultDto): boolean =>
    row.status === 'COMPETITION_NOT_FOUND' ||
    row.status === 'MATCH_NOT_FOUND' ||
    row.status === 'AMBIGUOUS'

/**
 * Alles, was in der Vorschau einen zweiten Blick verdient: die nicht zugeordneten Zeilen plus die
 * Duplikate, die den Import ohnehin blockieren (siehe hasBlockingImportRows in common.ts). Beides
 * zusammen ist das, was der Filter „Nur Auffälligkeiten zeigen“ übrig lässt - wer filtert, will die
 * blockierende Zeile schließlich auch sehen und nicht suchen müssen.
 */
export const isNoteworthyImportRow = (row: ImportRowResultDto): boolean =>
    isUnmatchedImportRow(row) || row.status === 'DUPLICATE'

export type ImportRowSummary = {
    total: number
    linked: number
    free: number
    unmatched: number
    duplicate: number
    noteworthy: number
}

/**
 * Zählt die Vorschau-Zeilen nach Kategorien für die Zusammenfassung über der Tabelle. Reine
 * Zählung ohne Formatierung, damit die Texte (inkl. Plural) in der Übersetzung bleiben.
 */
export const summarizeImportRows = (rows: ImportRowResultDto[]): ImportRowSummary => {
    const count = (predicate: (status: ImportRowStatus) => boolean) =>
        rows.filter(row => predicate(row.status)).length

    const unmatched = rows.filter(isUnmatchedImportRow).length
    const duplicate = count(status => status === 'DUPLICATE')

    return {
        total: rows.length,
        linked: count(status => status === 'LINKED'),
        free: count(status => status === 'FREE'),
        unmatched,
        duplicate,
        noteworthy: unmatched + duplicate,
    }
}

/**
 * Zeilen für die Tabelle: entweder alle, oder nur die Auffälligkeiten. Die Reihenfolge bleibt die
 * der Datei, damit die angezeigte Zeilennummer weiter zum Excel passt - umsortieren würde die
 * einzige verlässliche Brücke zwischen Vorschau und Datei kappen.
 */
export const visibleImportRows = (
    rows: ImportRowResultDto[],
    onlyNoteworthy: boolean,
): ImportRowResultDto[] => (onlyNoteworthy ? rows.filter(isNoteworthyImportRow) : rows)

/**
 * Der scharfe Import darf erst raus, wenn die nicht zugeordneten Zeilen bewusst bestätigt wurden.
 * Gibt es keine, bleibt alles wie bisher - kein zusätzlicher Klick für den Normalfall. Freie Slots
 * sind ein legitimer Anwendungsfall und am Regattatag muss man notfalls trotzdem importieren
 * können, deshalb bestätigen statt blockieren.
 */
export const needsUnmatchedConfirmation = (rows: ImportRowResultDto[]): boolean =>
    rows.some(isUnmatchedImportRow)
