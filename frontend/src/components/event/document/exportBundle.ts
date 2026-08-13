import {EventExportBundleItemDto} from '@api/types.gen.ts'

/**
 * Die Logik der Export-Mappe als pure Funktionen - Verwaltungs-Card und Export-Dialog halten nur
 * Zustand, alles Rechnen steht hier und ist ohne Komponente testbar (dasselbe Muster wie
 * startlistPreviewSelection.ts).
 */

export type MoveDirection = 'up' | 'down'

/**
 * Die neue Reihenfolge aller Einträge, nachdem [itemId] einen Platz gerückt ist - `null`, wenn der
 * Eintrag schon am Rand steht. Einfacher als bei den Kürzungsregeln: Die Mappe ist EINE Liste,
 * getauscht wird mit dem direkten Nachbarn.
 */
export const reorderedItemIds = (
    items: EventExportBundleItemDto[],
    itemId: string,
    direction: MoveDirection,
): string[] | null => {
    const index = items.findIndex(item => item.id === itemId)
    if (index < 0) {
        return null
    }

    const neighbour = direction === 'up' ? index - 1 : index + 1
    if (neighbour < 0 || neighbour >= items.length) {
        return null
    }

    const ids = items.map(item => item.id)
    ids[index] = items[neighbour].id
    ids[neighbour] = items[index].id
    return ids
}

/**
 * Nur PDF-Dateien gehören in die Mappe - die Tabelle kennt keinen Content-Type, entschieden wird
 * über den Dateinamen. Der Server überspringt Nicht-PDFs beim Zusammenbau ohnehin tolerant; das
 * hier hält sie von vornherein aus der Auswahl und begründet den Warnhinweis im Dialog.
 */
export const isPdfName = (name: string): boolean => name.toLowerCase().endsWith('.pdf')

/**
 * Der excludedBundleItems-Query-Parameter des Exports: die ABGEWÄHLTEN Einträge - der Server
 * kennt nur die Abwahl, damit neu hinzugekommene Dokumente nicht still herausfallen. undefined
 * (Parameter weglassen), wenn alles gewählt ist.
 */
export const excludedItemsParam = (
    items: EventExportBundleItemDto[],
    selected: Set<string>,
): string[] | undefined => {
    const excluded = items.filter(item => !selected.has(item.id)).map(item => item.id)
    return excluded.length === 0 ? undefined : excluded
}

/** Beim Laden der Mappe: alles vorausgewählt. */
export const initialBundleSelection = (items: EventExportBundleItemDto[]): Set<string> =>
    new Set(items.map(item => item.id))

export const toggleBundleItem = (selected: Set<string>, itemId: string): Set<string> => {
    const next = new Set(selected)
    if (next.has(itemId)) {
        next.delete(itemId)
    } else {
        next.add(itemId)
    }
    return next
}

/**
 * Ist mit dieser Auswahl der generierte Startlisten-Teil dabei? Ohne Platzhalter-Datensatz (die
 * Mappe wurde nie geladen) baut der Server die Startlisten ans Ende - deshalb zählt nur ein
 * VORHANDENER, abgewählter Platzhalter als "ohne Startlisten".
 */
export const includesGeneratedStartlists = (
    items: EventExportBundleItemDto[],
    selected: Set<string>,
): boolean => {
    const placeholder = items.find(item => item.kind === 'GENERATED_STARTLISTS')
    return placeholder === undefined || selected.has(placeholder.id)
}
