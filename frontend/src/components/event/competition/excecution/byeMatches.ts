import {CompetitionMatchDto, CompetitionRoundDto} from '@api/types.gen.ts'

/**
 * Die Aufteilung einer Runde in „gehört ins Freilos-Panel" und „bekommt eine Lauf-Karte".
 *
 * Beide fragen dasselbe Feld: `status.bye`, das der Server aus derselben Regel ableitet, nach der
 * er die Ergebniseingabe sperrt (`MatchStatusLogic.deriveBye`). Bis hierher stand die Regel ein
 * zweites Mal im Frontend als `teams.length === 1` — dieselbe Menge Läufe, aber eine zweite
 * Wahrheit, die auseinanderlaufen konnte.
 */
export const byeMatches = (round: CompetitionRoundDto): CompetitionMatchDto[] =>
    round.matches.filter(match => match.status.bye != null).sort((a, b) => a.weighting - b.weighting)

/**
 * Die Läufe, die als Karte erscheinen: alles, was kein Freilos ist und Mannschaften hat. Ein Lauf
 * ohne Mannschaften ist eine leere Hülle aus dem Turnierbaum und hat auf der Seite nichts verloren.
 */
export const raceableMatches = (round: CompetitionRoundDto): CompetitionMatchDto[] =>
    round.matches
        .filter(match => match.teams.length > 0 && match.status.bye == null)
        .sort((a, b) => a.executionOrder - b.executionOrder)
