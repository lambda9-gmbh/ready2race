import {CompetitionMatchDto} from '@api/types.gen.ts'

// "Fahrend" = eine Mannschaft, um die noch gerannt werden muss. `match.teams` enthält hier nie
// `out`-Mannschaften (die filtert schon CompetitionExecutionService.getProgress serverseitig heraus,
// bevor die Runde als CompetitionRoundDto rausgeht) - übrig bleibt nur noch `deregistered` als
// Ausschlussgrund, siehe CompetitionMatchTeamDto.
const isRacingTeam = (team: CompetitionMatchDto['teams'][number]): boolean => !team.deregistered

// Ein Lauf mit weniger als zwei fahrenden Mannschaften ist ein Freilos (vgl. das
// "Teams mit Freilos"-Panel, das dafür status.bye != null prüft, siehe byeMatches.ts) oder
// komplett leer - in beiden Fällen gibt es nichts zu fahren.
const matchHasNothingToRace = (match: CompetitionMatchDto): boolean =>
    match.teams.filter(isRacingTeam).length < 2

// Grundlage für "Runde entfällt" (Wettkampf → Durchführung): nur anbieten, wenn die Runde
// materialisiert ist (matches.length > 0 - das prüft der Server ohnehin noch einmal separat über
// EventScheduleRepo.countMatchesInRound) UND kein Lauf mehr zwei oder mehr tatsächlich fahrende
// Mannschaften hat. Sobald ein einziger Lauf noch gefahren werden müsste, muss er ausgetragen
// werden, damit die nächste Runde sauber ausgelost werden kann - die Aktion entfällt dann komplett.
export const roundHasNothingToRace = (matches: CompetitionMatchDto[]): boolean =>
    matches.length > 0 && matches.every(matchHasNothingToRace)
