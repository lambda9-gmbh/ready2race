import {ClubShortNameDto} from '@api/types.gen.ts'

/**
 * Was das Speichern einer Zeile auslösen soll.
 *
 * Der Grund für die Unterscheidung: das Eingabefeld ist mit der *automatisch* erzeugten Kurzform
 * vorbelegt, nicht leer - anders wären 46 Zeilen nicht in einer Sitzung durchzusehen. Damit sieht
 * eine unangetastete Zeile aber genauso aus wie eine ausgefüllte. Ohne diese Entscheidung würde
 * jedes Durchblättern den ganzen Bestand als "gepflegt" festschreiben, und eine spätere
 * Verbesserung der Heuristik käme nirgends mehr an.
 */
export type ClubShortNameAction = 'none' | 'save' | 'delete'

export const clubShortNameAction = (row: ClubShortNameDto, draft: string): ClubShortNameAction => {
    const trimmed = draft.trim()

    // Leeren heißt "zurück zur Heuristik". War nie etwas gepflegt, gibt es nichts zu löschen.
    if (trimmed === '') {
        return row.maintained ? 'delete' : 'none'
    }

    // Unverändert - egal ob der angezeigte Wert gepflegt ist oder aus der Heuristik kommt.
    if (trimmed === row.shortName) {
        return 'none'
    }

    return 'save'
}

/**
 * Was der Bearbeiten-Dialog eines Vereins als `shortName` mitschickt.
 *
 * Der Dialog schreibt in dieselbe Ablage wie die Liste, hat aber nur ein Feld statt einer Zeile
 * je Schreibweise - deshalb übersetzt diese Funktion die Entscheidung aus
 * [clubShortNameAction] in die drei Zustände des Feldes am Verein:
 *
 * - `undefined` - nicht mitschicken, die Ablage bleibt, wie sie ist
 * - `''` - Eintrag löschen, danach greift wieder die Automatik
 * - Wert - Kurzform pflegen
 *
 * [resolved] ist `null`, solange die aufgelöste Kurzform noch nicht geladen ist (und beim Anlegen
 * eines Vereins, zu dem es noch keine gibt). Dann gibt es nichts zu vergleichen: geschrieben wird
 * nur, was jemand hineingeschrieben hat.
 */
export const clubShortNameForRequest = (
    resolved: ClubShortNameDto | null,
    draft: string,
    mayUpdate: boolean,
): string | undefined => {
    // Ein gesperrtes Feld darf auch dann nichts auslösen, wenn der Verein umbenannt wird.
    if (!mayUpdate) {
        return undefined
    }

    if (resolved === null) {
        return draft.trim() === '' ? undefined : draft.trim()
    }

    switch (clubShortNameAction(resolved, draft)) {
        case 'none':
            return undefined
        case 'delete':
            return ''
        case 'save':
            return draft.trim()
    }
}

/** Die Schreibweise, die die Zeile anführt. Das Backend stellt die kürzeste nach vorn. */
export const primaryName = (row: ClubShortNameDto): string => row.names[0] ?? row.nameKey

/**
 * Die weiteren Schreibweisen, die unter demselben Schlüssel zusammengefasst wurden - auf der Seite
 * als "auch: …" untereinander.
 *
 * Diese Anzeige ist keine Zierde, sondern die einzige Kontrolle dagegen, dass die Normalisierung
 * zwei wirklich verschiedene Vereine verschmilzt. Ohne sie wäre so ein Fehler unsichtbar.
 */
export const mergedSpellings = (row: ClubShortNameDto): string[] => row.names.slice(1)
