import {CompetitionMatchDto} from '@api/types.gen.ts'

/**
 * Ob dieser Lauf irgendwo außerhalb des Durchführungs-Tabs zu sehen ist.
 *
 * Die vier Zustände sind genau die, in denen ihn eine Anzeige als Geschehen zeigt: an den Start
 * gerufen und unterwegs (Schiedsrichter-Dashboard, Athleten-Anzeige, öffentliche Anzeige), beendet
 * (Ergebnisse) und vollständig gewertet, aber noch nicht beendet — letzteres, weil die öffentliche
 * Ergebnisanzeige je nach `publicResultsVisibility` schon bei vollständigen Ergebnissen anzeigt,
 * nicht erst beim Beenden.
 *
 * Nicht dabei: anstehend, ungeplant und abgesagt. Diese Läufe stehen zwar im Zeitplan, aber ihre
 * Paarung hat noch niemand als Ereignis gesehen.
 *
 * Freilose fallen ebenfalls heraus, und zwar über `status.bye` aus `MatchStatusLogic.deriveBye` —
 * nicht über eine eigene Zählung der Mannschaften. Sonst stünde die Freilos-Regel hier ein zweites
 * Mal und könnte von der des Backends abweichen (siehe das KDoc von `byeMatches`). Ohne diesen Fall
 * meldete jede Runde mit einem Freilos für immer, sie sei schon zu sehen: Ein Freilos trägt seinen
 * Platz 1 seit der Erzeugung und gilt damit dauerhaft als vollständig gewertet.
 */
const matchIsOnDisplay = (match: CompetitionMatchDto): boolean =>
    match.status.bye == null &&
    (match.status.state === 'PREPARING' ||
        match.status.state === 'RUNNING' ||
        match.status.state === 'FINISHED' ||
        match.status.state === 'AWAITING_FINISH')

/**
 * Wie viele Läufe dieser Runde bereits irgendwo zu sehen sind — die Zahl, mit der der
 * Löschen-Dialog warnt.
 *
 * Löschen ist erlaubt und bleibt es: Das Regattabüro muss eine Runde auch dann zurücknehmen können,
 * wenn schon gefahren wurde. Aber es soll wissen, dass es damit etwas wegräumt, das draußen bereits
 * jemand gesehen hat — auf der Anzeige am Steg, auf der Athleten-Anzeige oder in den Ergebnissen.
 */
export const matchesOnDisplay = (matches: CompetitionMatchDto[]): number =>
    matches.filter(matchIsOnDisplay).length
