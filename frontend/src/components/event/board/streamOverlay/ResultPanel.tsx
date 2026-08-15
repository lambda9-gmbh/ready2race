import {Stack} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardResult, BoardElement} from '@api/types.gen.ts'
import FitToHeight from './FitToHeight.tsx'
import FlipList from '../FlipList.tsx'
import StreamBoatRow from './StreamBoatRow.tsx'
import StreamPanelShell from './StreamPanelShell.tsx'
import {byNullsLast, competitionLabel, roundMatchLabel, streamNameForms} from './streamDisplay.ts'

interface ResultPanelProps {
    result: AthleteBoardResult
    element: BoardElement
}

/**
 * „Ergebnis": zentriertes TV-Grafik-Panel, Boote nach Platz sortiert. Ein Ergebnis
 * kommt final an, eine Umsortierung ist nicht mehr zu erwarten — FlipList bleibt
 * trotzdem aktiv, sie ist im Ruhezustand kostenlos und fängt so auch eine nachträgliche
 * Korrektur (Schiedsrichter-Wertungsänderung) sauber ab.
 */
const ResultPanel = ({result, element}: ResultPanelProps) => {
    const {t} = useTranslation()
    const names = streamNameForms(element)
    const streamCrew = element.streamCrew ?? 'CLUBS_FIRST'
    const teams = [...result.teams].sort(byNullsLast(team => team.place))

    return (
        <StreamPanelShell
            panelKey={result.matchId}
            stateLabel={t('event.boards.stream.result')}
            title={competitionLabel(
                result.competitionName,
                result.competitionShortName,
                names.competitions,
            )}
            roundLine={roundMatchLabel(result.roundName, result.matchName)}>
            {/* Keine Bildlaufleiste auf einer TV-Grafik — eine Kachel scrollt nie; passt das
                Feld nicht in die Panelhöhe, verkleinert FitToHeight es, statt die letzte
                Bootszeile abzuschneiden. */}
            <FitToHeight>
                <Stack sx={{gap: 1.5}}>
                    <FlipList
                        items={teams}
                        keyOf={team => String(team.startNumber)}
                        render={team => (
                            <StreamBoatRow
                                team={team}
                                crewMode={streamCrew}
                                useShortClubNames={names.clubs}
                                failedFallback={t('event.info.athleteBoard.failed')}
                                deregisteredFallback={t('event.info.athleteBoard.deregistered')}
                                byeFallback={t('event.competition.execution.teamBye.label')}
                                size="large"
                            />
                        )}
                    />
                </Stack>
            </FitToHeight>
        </StreamPanelShell>
    )
}

export default ResultPanel
