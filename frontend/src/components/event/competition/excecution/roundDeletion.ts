import {CompetitionMatchDto} from '@api/types.gen.ts'

/**
 * Ein Freilos: ein einziger Lauf-Teilnehmer in einer nicht erforderlichen Runde. Dieselbe Regel wie
 * im Backend (`AutoRoundProgressionLogic.bye`) und im „Teams mit Freilos"-Panel.
 *
 * Freilose müssen hier gesondert behandelt werden, weil sie ihren Platz 1 seit der Erzeugung tragen
 * und damit dauerhaft als „vollständig gewertet" gelten — ohne diesen Fall meldete jede Runde mit
 * einem Freilos für immer, sie sei schon zu sehen.
 */
const isBye = (match: CompetitionMatchDto, roundRequired: boolean): boolean =>
    !roundRequired && match.teams.length === 1

/**
 * Ob dieser Lauf irgendwo außerhalb des Durchführungs-Tabs zu sehen ist.
 *
 * Die vier Zustände sind genau die, in denen ihn eine Anzeige als Geschehen zeigt: an den Start
 * gerufen und unterwegs (Schiedsrichter-Dashboard, Athleten-Anzeige, öffentliche Anzeige), beendet
 * (Ergebnisse) und vollständig gewertet, aber noch nicht beendet — letzteres, weil die
 * öffentliche Ergebnisanzeige je nach `publicResultsVisibility` schon bei vollständigen
 * Ergebnissen anzeigt, nicht erst beim Beenden.
 *
 * Nicht dabei: anstehend, ungeplant und abgesagt. Diese Läufe stehen zwar im Zeitplan, aber ihre
 * Paarung hat noch niemand als Ereignis gesehen.
 */
const matchIsOnDisplay = (match: CompetitionMatchDto, roundRequired: boolean): boolean =>
    !isBye(match, roundRequired) &&
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
export const matchesOnDisplay = (matches: CompetitionMatchDto[], roundRequired: boolean): number =>
    matches.filter(match => matchIsOnDisplay(match, roundRequired)).length
