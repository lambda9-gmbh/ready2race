import {LatestMatchResultInfo, MatchResultTeamInfo} from '@api/types.gen.ts'
import {sortByPlaces} from '@utils/helpers.ts'
import {groupByRatingCategory, RatingCategorySection} from '@utils/ratingCategorySections.ts'

export type FieldTeam = MatchResultTeamInfo & {own: boolean}

/**
 * Das komplette Feld eines Laufs für „Mein Event": nach Platz sortiert (Ausgeschiedene und
 * Abgemeldete ans Ende), in Abschnitte je Wertungskategorie gruppiert — dieselben Bausteine
 * wie auf der öffentlichen Ergebnisseite, damit beide Ansichten dieselbe Reihenfolge zeigen —
 * und mit markiertem eigenen Boot. Der Schlüssel dafür ist die eigene Meldung (`teamId` am
 * Ergebnis), nicht der Name: Namen doppeln sich, Meldungen nicht.
 */
export const fieldSections = (
    match: LatestMatchResultInfo,
    ownTeamId: string | null | undefined,
): RatingCategorySection<FieldTeam>[] =>
    groupByRatingCategory(
        sortByPlaces(
            match.teams.map(team => ({
                ...team,
                own: ownTeamId != null && team.teamId === ownTeamId,
            })),
        ),
        team => team.ratingCategory ?? null,
    )

/**
 * Der Platz, den eine Zeile zeigt: innerhalb der Wertungskategorie, wenn es eine gibt —
 * genau die Zahl, die auch die Ergebnisseite führt — sonst der Platz im Lauf.
 */
export const displayPlace = (team: MatchResultTeamInfo): number | null =>
    team.categoryPlace ?? team.place ?? null

/**
 * Die Bootszeile: die getragene Vereinskette, sonst der meldende Verein, zur Not der
 * Mannschaftsname. Leer bleibt sie nie, solange der Lauf überhaupt ein Boot hat.
 */
export const boatLabel = (team: MatchResultTeamInfo): string =>
    team.clubsShort ?? team.clubName ?? team.teamName ?? ''
