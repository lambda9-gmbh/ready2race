import {
    BoardCeremonyDto,
    BoardElement,
    BoardMatchSlotDto,
    BoardProgramEntry,
    BoardTile,
    BoardViewDto,
} from '@api/types.gen'
import {BoardContent, contentScale, MIN_DENSITY_SCALE} from '../info/athleteBoard/boardLayout'

/**
 * Pure Zuordnungslogik der Board-Anzeige: welches Element bekommt welchen Teil der
 * Board-Antwort, wie ordnen sich die Kacheln ins Raster, und wie groß wird der Text.
 * Ohne React und ohne Netz, damit sie — wie `boardLayout.ts` — einzeln prüfbar bleibt.
 */

export interface GridPlacement {
    /** Zeilenzahl des Rasters, aus den Kacheln abgeleitet. */
    rows: number
    /** Je Kachel die belegte Zelle (Spalte/Zeile, 1-basiert) — Reihenfolge wie `tiles`. */
    positions: {column: number; row: number; colSpan: number; rowSpan: number}[]
}

const spanOf = (tile: BoardTile, columns: number) => ({
    colSpan: Math.min(Math.max(tile.colSpan ?? 1, 1), columns),
    rowSpan: Math.max(tile.rowSpan ?? 1, 1),
})

/**
 * Bildet die CSS-Auto-Platzierung nach (row-major, Cursor läuft nur vorwärts): die
 * Kacheln fließen in Reihenfolge ins Raster, eine zu breite Kachel rückt in die nächste
 * Zeile. Die Anzeige braucht daraus die Zeilenzahl (für `1fr`-Zeilen ohne Überlauf) und
 * die Positionen (damit Editor-Vorschau und Bühne dieselbe Anordnung zeigen).
 */
export const gridPlacement = (tiles: BoardTile[], columns: number): GridPlacement => {
    const occupied = new Set<string>()
    const positions: GridPlacement['positions'] = []
    let cursorRow = 1
    let cursorCol = 1
    let rows = 1

    const fits = (row: number, col: number, colSpan: number, rowSpan: number) => {
        if (col + colSpan - 1 > columns) return false
        for (let r = row; r < row + rowSpan; r++) {
            for (let c = col; c < col + colSpan; c++) {
                if (occupied.has(`${r}:${c}`)) return false
            }
        }
        return true
    }

    for (const tile of tiles) {
        const {colSpan, rowSpan} = spanOf(tile, columns)
        let row = cursorRow
        let col = cursorCol
        while (!fits(row, col, colSpan, rowSpan)) {
            col++
            if (col > columns) {
                col = 1
                row++
            }
        }
        for (let r = row; r < row + rowSpan; r++) {
            for (let c = col; c < col + colSpan; c++) {
                occupied.add(`${r}:${c}`)
            }
        }
        positions.push({column: col, row, colSpan, rowSpan})
        rows = Math.max(rows, row + rowSpan - 1)
        cursorRow = row
        cursorCol = col
    }

    return {rows, positions}
}

/** Beide Hex-Formen der Konfiguration — dieselben zwei, die die Backend-Validierung durchlässt. */
const HEX_COLOR = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/

/**
 * Prüft eine konfigurierte Kachelfarbe (Fläche oder Rand) fürs Rendering: gültiges Hex
 * (`#RGB` oder `#RRGGBB`) geht unverändert durch, alles andere ergibt `undefined` —
 * dann bleibt das bisherige Aussehen. Der Filter schützt die Anzeige vor Alt- oder
 * Handständen in der Datenbank, die die Backend-Validierung nie gesehen hat.
 */
export const tileColor = (color: string | null | undefined): string | undefined =>
    color != null && HEX_COLOR.test(color) ? color : undefined

/**
 * Elementtypen, die von sich aus klein bleiben: feste clamp()-Schrift statt Dichteformel,
 * kein wachsender Listeninhalt. Eine Rasterzeile, deren Kacheln AUSSCHLIESSLICH solche
 * Elemente tragen, braucht keine 1fr-Höhe — sie schrumpft auf Inhaltshöhe und schenkt
 * die gewonnene Höhe den Inhalts-Zeilen (Nutzerentscheidung 12.08.2026: automatisch,
 * kein Schalter). Bewusst als erweiterbares Set, falls weitere „statisch kleine" Typen
 * dazukommen.
 */
const COMPACT_ELEMENT_TYPES: ReadonlySet<BoardElement['type']> = new Set(['CLOCK', 'DELAY'])

/** Die Höhenklasse einer Rasterzeile: Inhalts-Zeile ('1fr') oder Kompakt-Zeile ('auto'). */
export type RowSize = '1fr' | 'auto'

/**
 * Die Höhenklassen aller Rasterzeilen, aus den Platzierungen von [gridPlacement]:
 * eine Zeile ist nur dann kompakt ('auto'), wenn KEINE Inhalts-Kachel sie berührt.
 * Eine Inhalts-Kachel mit rowSpan macht dabei alle überspannten Zeilen zu '1fr' —
 * sie braucht ihre Höhe über die ganze Spannweite. Rotiert eine Kachel Uhr UND Lauf,
 * zählt sie als Inhalt (every über die Elemente). Bühne (BoardRenderer) und
 * Editor-Vorschau (BoardEditor) lesen dieselbe Funktion, damit beide dieselben
 * Zeilen schrumpfen.
 */
export const rowSizes = (
    tiles: BoardTile[],
    positions: GridPlacement['positions'],
    rows: number,
): RowSize[] => {
    const sizes: RowSize[] = Array.from({length: rows}, () => 'auto')
    tiles.forEach((tile, index) => {
        // Leere Kacheln (validierungswidrig, aber denkbar in Altdaten) gelten als
        // Inhalt — im Zweifel lieber eine zu hohe Zeile als eine zerquetschte.
        const compact =
            tile.elements.length > 0 &&
            tile.elements.every(element => COMPACT_ELEMENT_TYPES.has(element.type))
        if (compact) return
        const position = positions[index]
        for (let row = position.row; row < position.row + position.rowSpan; row++) {
            sizes[row - 1] = '1fr'
        }
    })
    return sizes
}

/**
 * Der Timeline-Slot eines Lauf-Elements (MATCH und die Sprecher-Kachel MATCH_DETAIL —
 * beide wählen über denselben Offset). `null` heißt: die Antwort trägt diesen Offset
 * nicht (Konfiguration und Daten stammen aus verschiedenen Ständen) — die Kachel zeigt
 * dann ihren Leerzustand, statt einen falschen Slot zu greifen.
 */
export const slotForElement = (
    view: BoardViewDto,
    element: BoardElement,
): BoardMatchSlotDto | null =>
    (element.type === 'MATCH' || element.type === 'MATCH_DETAIL') && element.offset != null
        ? (view.slots.find(slot => slot.offset === element.offset) ?? null)
        : null

/**
 * Ob das Board eine Sprecher-Kachel (MATCH_DETAIL) enthält. Der Editor sperrt dann das
 * Hinzufügen weiterer Kacheln — die Vollbild-Regel, die das Backend beim Speichern
 * erzwingt, soll in der Maske gar nicht erst verletzbar sein.
 */
export const hasMatchDetail = (tiles: BoardTile[]): boolean =>
    tiles.some(tile => tile.elements.some(element => element.type === 'MATCH_DETAIL'))

/**
 * Die wirksame Spaltenzahl eines Boards. Sonderfall Sprecher-Kachel (Nutzer-Befund
 * 12.08.2026): ein Board, dessen EINZIGE Kachel MATCH_DETAIL enthält, rendert immer
 * vollflächig — die konfigurierten columns/colSpan werden ignoriert statt abgelehnt.
 * Die Backend-Validierung prüft nur „einzige Kachel", nicht die Rastergeometrie;
 * mit 3 gewählten Spalten quetschte sich die Kachel in eine Spalte. Ignorieren statt
 * neuer Validierungsfehler heilt auch GESPEICHERTE Fehlkonfigurationen sofort, ohne
 * Migration der Configs. Bühne (BoardRenderer) und Editor-Vorschau lesen dieselbe
 * Funktion, damit beide dasselbe zeigen.
 */
export const boardColumns = (config: {columns?: number | null; tiles: BoardTile[]}): number =>
    config.tiles.length === 1 && hasMatchDetail(config.tiles) ? 1 : (config.columns ?? 3)

/**
 * Die Daten eines Listen-Elements, auf sein eigenes Limit zugeschnitten. Der Server
 * liefert je Modus das größte Limit aller Elemente; das Zuschneiden je Element
 * passiert hier.
 */
export const listForElement = (view: BoardViewDto, element: BoardElement) => {
    if (element.type !== 'MATCH_LIST' || element.listMode == null) return null
    const list = view.lists.find(l => l.mode === element.listMode)
    if (!list) return null
    const limit = element.limit ?? list.matches.length + list.results.length
    return {
        mode: list.mode,
        matches: list.matches.slice(0, limit),
        results: list.results.slice(0, limit),
    }
}

/** Die Ehrung eines Siegerehrungs-Elements — null, wenn die Antwort sie (noch) nicht trägt. */
export const ceremonyForElement = (
    view: BoardViewDto,
    element: BoardElement,
): BoardCeremonyDto | null =>
    element.type === 'AWARD_CEREMONY' && element.competitionId != null
        ? (view.ceremonies?.find(
              c =>
                  c.competitionId === element.competitionId &&
                  (c.ratingCategoryId ?? null) === (element.ratingCategoryId ?? null),
          ) ?? null)
        : null

/** Wie viele beendete Zeilen das Tagesprogramm als Kontext behält, bevor es zuschneidet. */
const PROGRAM_FINISHED_CONTEXT = 2

/**
 * Der Ausschnitt des Tagesprogramms für ein Listen-Element. Zwei Modi (scheduleMode):
 * FULL liefert den ganzen Tag ohne Zuschnitt — die Kachel scrollt stattdessen.
 * FOLLOW (Default, auch für Alt-Konfigurationen ohne das Feld) zentriert um „jetzt" —
 * ein paar beendete Läufe als Kontext, dann Laufendes und Anstehendes bis zum Limit.
 * Der Server liefert bewusst den ganzen Tag; erst hier wird zugeschnitten, damit
 * dieselbe Antwort Kacheln mit verschiedenen Limits bedienen kann.
 */
export const programForElement = (
    view: BoardViewDto,
    element: BoardElement,
): BoardProgramEntry[] | null => {
    if (element.type !== 'MATCH_LIST' || element.listMode !== 'SCHEDULE') return null
    const program = view.lists.find(l => l.mode === 'SCHEDULE')?.program ?? []
    // FULL ignoriert auch das Limit: „ganzer Tag" heißt ganzer Tag, der gespeicherte
    // Limit-Wert bleibt nur für einen späteren Rückwechsel auf FOLLOW erhalten.
    if (element.scheduleMode === 'FULL') return program
    const limit = element.limit ?? program.length
    const firstOpen = program.findIndex(e => e.state !== 'FINISHED')
    const start = firstOpen === -1
        ? Math.max(0, program.length - limit)
        : Math.max(0, firstOpen - PROGRAM_FINISHED_CONTEXT)
    return program.slice(start, start + limit)
}

/**
 * Die Dichteformel stammt von der einzeiligen Bühne; belegt eine Kachel nur einen Teil
 * der Rasterhöhe, muss die Schrift eine Stufe kleiner. Der Faktor ist am Sichttest vom
 * 10.08.2026 abgelesen: ohne ihn überlappten sich die Bootszeilen eines Dreierfelds in
 * der oberen Kachelreihe.
 */
const PARTIAL_HEIGHT_FACTOR = 0.7

/**
 * Der Schriftfaktor eines Lauf-Elements: die Dichteformel der alten Bühne, je Kachel
 * angewandt. [effectiveColumns] ist der Breitenanteil (Rasterspalten ÷ colSpan — eine
 * 2-von-3-Spalten-Kachel verhält sich wie 1,5 Spalten), [heightFraction] der
 * Höhenanteil (rowSpan ÷ Zeilenzahl). `autoFit === false` schaltet die Formel ab —
 * dann bleibt die volle Größe stehen und die Kachel darf scrollen bzw. abschneiden.
 */
export const elementScale = (
    element: BoardElement,
    content: BoardContent | null,
    effectiveColumns: number,
    heightFraction: number = 1,
): number => {
    if (element.autoFit === false) return 1
    const base = contentScale(content ? [content] : [], effectiveColumns)
    return heightFraction <= 0.5 ? Math.max(MIN_DENSITY_SCALE, base * PARTIAL_HEIGHT_FACTOR) : base
}
