import {AthleteBoardDto, AthleteBoardMatch, AthleteBoardResult} from '@api/types.gen'

/**
 * Wie viele Läufe in der Arena gleichzeitig auf die Bühne kommen.
 *
 * Auf einer Regatta sind höchstens zwei Läufe gleichzeitig relevant. Eine Anzeige, die auf
 * beliebig viele auslegt, zahlt dafür mit Enge, ohne den Nutzen je zu sehen. Was darüber
 * hinausgeht, verschwindet nicht stumm, sondern wird über [BoardLayout.hiddenRunning] gemeldet.
 */
export const MAX_RUNNING_CARDS = 2

export type BoardCardKind = 'running' | 'upcoming' | 'result'

/**
 * Eine Spalte der Bühne. Genau eines von [match] und [result] ist gefüllt — oder keines, dann
 * steht die Statusspalte leer da und zeigt ihre neutrale Zeile.
 */
export interface BoardCard {
    kind: BoardCardKind
    /** React-Schlüssel; für eine leere Statusspalte der Status selbst. */
    key: string
    match: AthleteBoardMatch | null
    result: AthleteBoardResult | null
}

export interface BoardLayout {
    cards: BoardCard[]
    /** Läufe in der Arena, für die kein Platz war. */
    hiddenRunning: number
}

/**
 * Was auf die Bühne kommt, in fester Reihenfolge: die Läufe in der Arena (höchstens zwei), der
 * nächste Lauf, das letzte Ergebnis.
 *
 * Die drei Statusspalten stehen immer, auch leer. Ein fest montierter Bildschirm soll seine
 * Struktur nicht wechseln, nur weil gerade nichts fährt — wer täglich davorsteht, findet seine
 * Spalte über die Position, nicht über die Überschrift.
 */
export const selectBoardCards = (data: AthleteBoardDto | null): BoardLayout => {
    const running = data?.running ?? []
    const shownRunning = running.slice(0, MAX_RUNNING_CARDS)

    const runningCards: BoardCard[] =
        shownRunning.length > 0
            ? shownRunning.map(match => ({
                  kind: 'running' as const,
                  key: match.matchId,
                  match,
                  result: null,
              }))
            : [{kind: 'running' as const, key: 'running-empty', match: null, result: null}]

    const upcoming = data?.upcoming?.[0] ?? null
    const latest = data?.results?.[0] ?? null

    return {
        cards: [
            ...runningCards,
            {
                kind: 'upcoming',
                key: upcoming?.matchId ?? 'upcoming-empty',
                match: upcoming,
                result: null,
            },
            {
                kind: 'result',
                key: latest?.matchId ?? 'result-empty',
                match: null,
                result: latest,
            },
        ],
        hiddenRunning: running.length - shownRunning.length,
    }
}

const boatsInCard = (card: BoardCard): number =>
    card.match?.teams.length ?? card.result?.teams.length ?? 0

/** Das vollste Boot-Feld auf der Bühne — es bestimmt, wie eng es überall wird. */
export const maxBoats = (cards: BoardCard[]): number =>
    cards.reduce((max, card) => Math.max(max, boatsInCard(card)), 0)

/** Unterhalb dieser Größe wäre der Text aus fünf Metern nicht mehr zu lesen. */
export const MIN_DENSITY_SCALE = 0.55

/**
 * Oberhalb dieser Größe würde der Text unförmig, obwohl das Feld klein ist — ein Rennen mit
 * zwei Booten muss die freie Fläche nutzen dürfen, aber nicht grenzenlos.
 */
export const MAX_DENSITY_SCALE = 1.5

/** Bis zu so vielen Booten und so vielen Spalten bleibt die Anzeige in voller Größe. */
const BOATS_AT_FULL_SIZE = 4
const COLUMNS_AT_FULL_SIZE = 3

const BOAT_STEP = 0.06
const COLUMN_STEP = 0.09

/**
 * Der Faktor, mit dem alle Schriftgrößen der Bühne multipliziert werden.
 *
 * Das Layout kann baulich nicht überlaufen — die Bootszeilen teilen sich als `1fr` die Höhe, die
 * da ist. Diese Funktion entscheidet, wie groß der Text dabei wird: Der Faktor wirkt in beide
 * Richtungen. Unterhalb von BOATS_AT_FULL_SIZE Booten bleibt in der Zeile Platz übrig, den ein
 * kleines Feld beanspruchen darf — der Faktor steigt über 1, bis höchstens MAX_DENSITY_SCALE,
 * damit ein einzelnes Boot auf einer großen Wand nicht klein bleibt, nur weil die Formel sonst
 * bei 100 % aufhören würde. Oberhalb von BOATS_AT_FULL_SIZE Booten bzw. COLUMNS_AT_FULL_SIZE
 * Spalten sinkt er, damit ein volles Feld nicht in seinem Kartenrahmen erdrückt wird, bis
 * höchstens hinunter zu MIN_DENSITY_SCALE. Bewusst ohne Messung im Browser: ein Bildschirm, der
 * tagelang unbeaufsichtigt läuft, soll bei einer Größenänderung nichts neu entscheiden müssen.
 */
export const densityScale = (boats: number, columns: number): number => {
    // Ohne die Untergrenze bei 0 wirkt ein kleines Feld hier auch negativ und hebt den Faktor an
    // — das ist beabsichtigt (siehe MAX_DENSITY_SCALE oben). Bei den Spalten gibt es diesen Fall
    // nicht: die Bühne zeigt immer mindestens drei Statusspalten, also bleibt es bei der reinen
    // Verkleinerung ab COLUMNS_AT_FULL_SIZE.
    const forBoats = BOAT_STEP * (boats - BOATS_AT_FULL_SIZE)
    const forColumns = COLUMN_STEP * Math.max(0, columns - COLUMNS_AT_FULL_SIZE)
    return Math.min(MAX_DENSITY_SCALE, Math.max(MIN_DENSITY_SCALE, 1 - forBoats - forColumns))
}
