import {RatingCategoryRefDto} from '@api/types.gen.ts'

/**
 * Ein Ergebnisabschnitt: alle Boote einer Wertungskategorie. `category` ist null für den
 * Abschnitt „Ohne Wertungskategorie", der immer am Ende steht.
 */
export type RatingCategorySection<T> = {
    category: RatingCategoryRefDto | null
    entries: T[]
}

/**
 * Gruppiert eine Ergebnisliste in Abschnitte je Wertungskategorie, in der für die Veranstaltung
 * konfigurierten Reihenfolge (`sortOrder`, bei Gleichstand der Name). Der Abschnitt ohne
 * Kategorie hängt hinten an, auch wenn eine echte Kategorie eine höhere `sortOrder` trägt.
 *
 * Die Platzierung *innerhalb* der Kategorie rechnet das Backend
 * (`RatingCategoryRanking.groupAndRank`) und liefert sie als `categoryPlace` mit — hier wird nur
 * gruppiert. Diese Funktion teilen sich öffentliche Ergebnisseite, Athleten-Anzeige,
 * Schiedsrichter-Dashboard und Platzierungsansicht, damit alle vier dieselben Abschnitte in
 * derselben Reihenfolge zeigen.
 *
 * Die Reihenfolge innerhalb eines Abschnitts bleibt die der Eingabeliste; wer nach Platz sortiert
 * haben will, sortiert vorher (`sortByPlaces`).
 *
 * [categoryOf] wird ausdrücklich übergeben statt fest auf ein Feld `ratingCategory` zu zeigen: die
 * vier Ansichten arbeiten mit vier verschiedenen DTOs, und eine davon (der laufende Lauf) kennt gar
 * keine Kategorie.
 */
export const groupByRatingCategory = <T>(
    entries: readonly T[],
    categoryOf: (entry: T) => RatingCategoryRefDto | null | undefined,
): RatingCategorySection<T>[] => {
    const sections = new Map<string, RatingCategorySection<T>>()

    for (const entry of entries) {
        const category = categoryOf(entry) ?? null
        const key = category?.id ?? ''
        const section = sections.get(key)
        if (section) {
            section.entries.push(entry)
        } else {
            sections.set(key, {category, entries: [entry]})
        }
    }

    return [...sections.values()].sort((a, b) => {
        if (a.category === null) return b.category === null ? 0 : 1
        if (b.category === null) return -1
        return (
            a.category.sortOrder - b.category.sortOrder ||
            a.category.name.localeCompare(b.category.name)
        )
    })
}

/**
 * Ob die Anzeige überhaupt in Abschnitte trennen muss. Eine Veranstaltung ohne Wertungskategorien
 * liefert genau einen namenlosen Abschnitt - dort wäre eine Überschrift „Ohne Wertungskategorie"
 * über der einzigen Liste nur Lärm.
 */
export const hasRatingCategories = <T>(sections: RatingCategorySection<T>[]): boolean =>
    sections.some(section => section.category !== null)
