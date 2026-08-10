import {AthleteBoardMatch, AthleteBoardResult} from '@api/types.gen'

/**
 * Die Messfunktionen der Anzeige-Dichte. Bis zum Board-Umbau (10.08.2026) wählte dieses
 * Modul auch die Spalten der festen Bühne aus (`selectBoardCards`); diese Entscheidung
 * trifft jetzt der Server über die Timeline-Slots der Board-Antwort. Geblieben ist, was
 * je Kachel weiter gebraucht wird: wie voll ein Feld ist, wie lang die längste
 * Vereinskette, und welcher Schriftfaktor daraus folgt.
 */

/** Der Inhalt einer Kachel, soweit er für die Dichte zählt. Höchstens eines von beiden gefüllt. */
export interface BoardContent {
    match: AthleteBoardMatch | null
    result: AthleteBoardResult | null
}

const boatsInContent = (content: BoardContent): number =>
    content.match?.teams.length ?? content.result?.teams.length ?? 0

/** Das vollste Boot-Feld — es bestimmt, wie eng es überall wird. */
export const maxBoats = (contents: BoardContent[]): number =>
    contents.reduce((max, content) => Math.max(max, boatsInContent(content)), 0)

const teamsInContent = (
    content: BoardContent,
): {clubsFull?: string | null; teamName?: string | null}[] =>
    content.match?.teams ?? content.result?.teams ?? []

/**
 * Die längste Mannschaftszeile, in Zeichen.
 *
 * Gezählt wird die ausgeschriebene Vereinskette samt Mannschaftsname — die Form, die ab `md` in
 * der Zeile steht (siehe `AthleteBoardTeamLabel`). Die Kurzform darunter ist unkritisch, dort
 * scrollt die Seite ohnehin.
 */
export const longestTeamLabel = (contents: BoardContent[]): number =>
    contents.reduce(
        (max, content) =>
            teamsInContent(content).reduce(
                (inner, team) =>
                    Math.max(inner, (team.clubsFull?.length ?? 0) + (team.teamName?.length ?? 0)),
                max,
            ),
        0,
    )

/**
 * Wie viele Zeichen eine Spalte bei drei Spalten in eine Textzeile bekommt. Grober Richtwert, am
 * Sichttest vom 10.08.2026 abgelesen: bei drei Spalten auf 2560 px passte „Ruderverein Flensburg
 * von 1910 e.V." (35 Zeichen) in eine Zeile, bei vier Spalten brach schon „Ruderverein Flensburg"
 * um.
 */
const LABEL_CHARS_PER_LINE_AT_FULL_SIZE = 34

/**
 * Wie viele Textzeilen eine Bootszeile höchstens trägt.
 *
 * Zwei im Normalfall: Vereinskette plus die kleine Zeile darunter (Crew bzw. Startnummer). Passt
 * die Kette nicht in eine Zeile, klammert `AthleteBoardTeamLabel` sie auf zwei — dann sind es
 * drei. Mehr kann es nicht werden, die Klammer deckelt bei zwei Zeilen und die kleine Zeile
 * bricht nie um.
 */
export const rowTextLines = (longestLabel: number, columns: number): number => {
    const perLine = Math.max(
        1,
        Math.round((LABEL_CHARS_PER_LINE_AT_FULL_SIZE * COLUMNS_AT_FULL_SIZE) / Math.max(columns, 1)),
    )
    return longestLabel > perLine ? 3 : 2
}

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

/** Zeilen, die eine Bootszeile im Normalfall trägt: Vereinskette plus die kleine Zeile darunter. */
const LINES_AT_FULL_SIZE = 2
const LINE_STEP = 0.08

/**
 * Der Faktor, mit dem alle Schriftgrößen einer Kachel multipliziert werden.
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
export const densityScale = (
    boats: number,
    columns: number,
    textLines: number = LINES_AT_FULL_SIZE,
): number => {
    // Ohne die Untergrenze bei 0 wirkt ein kleines Feld hier auch negativ und hebt den Faktor an
    // — das ist beabsichtigt (siehe MAX_DENSITY_SCALE oben). Bei den Spalten gibt es diesen Fall
    // nicht: unter COLUMNS_AT_FULL_SIZE Spalten bleibt es bei der reinen Verkleinerung über die
    // Boote.
    const forBoats = BOAT_STEP * (boats - BOATS_AT_FULL_SIZE)
    const forColumns = COLUMN_STEP * Math.max(0, columns - COLUMNS_AT_FULL_SIZE)
    // Umbrechende Vereinsketten machen jede Bootszeile eine Textzeile höher. Nur nach unten: eine
    // kurze Kette spart keine Höhe, sie lässt nur Luft.
    const forLines = LINE_STEP * Math.max(0, textLines - LINES_AT_FULL_SIZE)
    return Math.min(
        MAX_DENSITY_SCALE,
        Math.max(MIN_DENSITY_SCALE, 1 - forBoats - forColumns - forLines),
    )
}

/**
 * Der Skalierungsfaktor einer Kachel, aus ihrem Inhalt und der Spaltenzahl des Boards
 * abgeleitet — dieselbe Verdrahtung, die bis zum Umbau `boardScale` für die ganze Bühne
 * traf, jetzt je Kachel.
 */
export const contentScale = (contents: BoardContent[], columns: number): number =>
    densityScale(
        maxBoats(contents),
        columns,
        rowTextLines(longestTeamLabel(contents), columns),
    )
