import {CompetitionMatchDto, CompetitionRoundDto} from '@api/types.gen.ts'

/**
 * Die Aufteilung einer Runde in „gehört ins Freilos-Panel" und „bekommt eine Lauf-Karte".
 *
 * Beide fragen dasselbe Feld: `status.bye` aus `MatchStatusLogic.deriveBye`. Dessen Regel ist die
 * dieses Panels — Runde nicht verpflichtend, genau eine nicht als `out` mitgeführte Mannschaft —,
 * die bis hierher ein zweites Mal im Frontend als `teams.length === 1` stand: dieselbe Menge Läufe,
 * aber eine zweite Wahrheit, die auseinanderlaufen konnte.
 *
 * **Nicht zu verwechseln mit der Ergebnissperre.** `CompetitionExecutionService.checkUpdateMatchResult`
 * prüft die *ungefilterte* Teamliste und greift deshalb beim mitgeführten `out`-Gegner nicht — ein
 * hier als Freilos angezeigter Lauf kann serverseitig weiter Ergebnisse annehmen. Das ist bekannt und
 * steht als M16 im Testkatalog; siehe das KDoc von `deriveBye`.
 */
export const byeMatches = (round: CompetitionRoundDto): CompetitionMatchDto[] =>
    round.matches.filter(match => match.status.bye != null).sort((a, b) => a.weighting - b.weighting)

/**
 * Die Läufe, die als Karte erscheinen: alles, was kein Freilos ist und Mannschaften hat. Ein Lauf
 * ohne Mannschaften ist eine leere Hülle aus dem Turnierbaum und hat auf der Seite nichts verloren.
 *
 * Ein Freilos mit „muss gefahren werden" (`bye.mustRace`) bekommt BEIDES: seine Lauf-Karte (es
 * wird gefahren, Startliste/Ergebnisse/Beenden laufen dort wie bei jedem Lauf) UND seinen Eintrag
 * im Freilos-Panel (dort sitzt der Schalter, und dass es ein Freilos bleibt, soll sichtbar sein).
 */
export const raceableMatches = (round: CompetitionRoundDto): CompetitionMatchDto[] =>
    round.matches
        .filter(
            match =>
                match.teams.length > 0 &&
                (match.status.bye == null || match.status.bye.mustRace),
        )
        .sort((a, b) => a.executionOrder - b.executionOrder)
