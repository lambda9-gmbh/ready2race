import {Stack} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardResult, BoardElement} from '@api/types.gen.ts'
import FlipList from '../FlipList.tsx'
import StreamBoatRow from './StreamBoatRow.tsx'
import StreamPanelShell from './StreamPanelShell.tsx'
import {byNullsLast, competitionLabel, roundMatchLabel} from './streamDisplay.ts'

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
    const useShortNames = element.useShortNames !== false
    const streamCrew = element.streamCrew ?? 'CLUBS_FIRST'
    const teams = [...result.teams].sort(byNullsLast(team => team.place))

    return (
        <StreamPanelShell
            panelKey={result.matchId}
            stateLabel={t('event.boards.stream.result')}
            title={competitionLabel(result.competitionName, result.competitionShortName, useShortNames)}
            roundLine={roundMatchLabel(result.roundName, result.matchName)}>
            <Stack sx={{gap: 1.5, overflow: 'auto'}}>
                <FlipList
                    items={teams}
                    keyOf={team => String(team.startNumber)}
                    render={team => (
                        <StreamBoatRow
                            team={team}
                            crewMode={streamCrew}
                            useShortNames={useShortNames}
                            failedFallback={t('event.info.athleteBoard.failed')}
                            size="large"
                        />
                    )}
                />
            </Stack>
        </StreamPanelShell>
    )
}

export default ResultPanel
