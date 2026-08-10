import {BoardElement, BoardLayout, BoardMatchSlotDto, BoardViewDto} from '@api/types.gen'
import {BoardContent, contentScale, MIN_DENSITY_SCALE} from '../info/athleteBoard/boardLayout'

/**
 * Pure Zuordnungslogik der Board-Anzeige: welches Element bekommt welchen Teil der
 * Board-Antwort, und wie groß wird sein Text. Ohne React und ohne Netz, damit sie —
 * wie `boardLayout.ts` — einzeln prüfbar bleibt.
 */

/** Das Raster eines Layouts. 6 Kacheln stehen als 3×2. */
export const gridForLayout = (layout: BoardLayout): {columns: number; rows: number} => {
    switch (layout) {
        case 'ONE_COLUMN':
            return {columns: 1, rows: 1}
        case 'TWO_COLUMNS':
            return {columns: 2, rows: 1}
        case 'THREE_COLUMNS':
            return {columns: 3, rows: 1}
        case 'SIX_TILES':
            return {columns: 3, rows: 2}
    }
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
 * Die Dichteformel stammt von der einzeiligen Bühne; im 3×2-Raster hat eine Kachel nur
 * die halbe Höhe. Der Faktor ist am Sichttest vom 10.08.2026 abgelesen: ohne ihn
 * überlappten sich die Bootszeilen eines Dreierfelds in der oberen Kachelreihe.
 */
const HALF_HEIGHT_FACTOR = 0.7

/**
 * Der Schriftfaktor eines Lauf-Elements: die Dichteformel der alten Bühne, je Kachel
 * angewandt. `autoFit === false` schaltet sie ab — dann bleibt die volle Größe stehen
 * und die Kachel darf scrollen bzw. abschneiden.
 */
export const elementScale = (
    element: BoardElement,
    content: BoardContent | null,
    columns: number,
    rows: number = 1,
): number => {
    if (element.autoFit === false) return 1
    const base = contentScale(content ? [content] : [], columns)
    return rows > 1 ? Math.max(MIN_DENSITY_SCALE, base * HALF_HEIGHT_FACTOR) : base
}
