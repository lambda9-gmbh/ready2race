import {BoardElement, BoardMatchSlotDto, BoardTile, BoardViewDto} from '@api/types.gen'
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

/**
 * Der Timeline-Slot eines Lauf-Elements. `null` heißt: die Antwort trägt diesen Offset
 * nicht (Konfiguration und Daten stammen aus verschiedenen Ständen) — die Kachel zeigt
 * dann ihren Leerzustand, statt einen falschen Slot zu greifen.
 */
export const slotForElement = (
    view: BoardViewDto,
    element: BoardElement,
): BoardMatchSlotDto | null =>
    element.type === 'MATCH' && element.offset != null
        ? (view.slots.find(slot => slot.offset === element.offset) ?? null)
        : null

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
