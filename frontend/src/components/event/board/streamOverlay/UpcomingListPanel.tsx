import {Stack, Typography, useTheme} from '@mui/material'
import {format} from 'date-fns'
import {useTranslation} from 'react-i18next'
import {AthleteBoardMatch, BoardElement} from '@api/types.gen.ts'
import FlipList from '../FlipList.tsx'
import StreamPanelShell from './StreamPanelShell.tsx'
import {competitionLabel, roundMatchLabel, solidOr} from './streamDisplay.ts'

interface UpcomingListPanelProps {
    matches: AthleteBoardMatch[]
    element: BoardElement
}

/**
 * Modus „Nächste Läufe": zentriertes Panel mit den nächsten (bis zu fünf) anstehenden
 * Läufen — eine Zeile je Lauf mit Startzeit, Wettkampf und Runde/Lauf.
 */
const UpcomingListPanel = ({matches, element}: UpcomingListPanelProps) => {
    const {t} = useTranslation()
    const theme = useTheme()
    const useShortNames = element.useShortNames !== false

    return (
        <StreamPanelShell
            panelKey="upcoming-list"
            title={t('event.boards.stream.upcomingListTitle')}
            roundLine={null}>
            {/* Keine Bildlaufleiste auf einer TV-Grafik — eine Kachel scrollt nie. */}
            <Stack sx={{gap: 1.5, overflow: 'hidden'}}>
                <FlipList
                    items={matches}
                    keyOf={match => match.matchId}
                    render={match => {
                        const round = roundMatchLabel(match.roundName, match.matchName)
                        return (
                            <Stack direction="row" alignItems="baseline" gap={2} sx={{width: 1}}>
                                <Typography
                                    variant="h5"
                                    sx={{
                                        fontWeight: 700,
                                        fontVariantNumeric: 'tabular-nums',
                                        minWidth: '4em',
                                        flexShrink: 0,
                                    }}>
                                    {match.startTime ? format(new Date(match.startTime), t('format.time')) : '–'}
                                </Typography>
                                <Typography variant="h5" noWrap sx={{fontWeight: 600, minWidth: 0, flex: 1}}>
                                    {competitionLabel(match.competitionName, match.competitionShortName, useShortNames)}
                                </Typography>
                                {round && (
                                    <Typography
                                        variant="body1"
                                        noWrap
                                        sx={{flexShrink: 0, color: solidOr(theme.palette.text.secondary, '#cccccc')}}>
                                        {round}
                                    </Typography>
                                )}
                            </Stack>
                        )
                    }}
                />
            </Stack>
        </StreamPanelShell>
    )
}

export default UpcomingListPanel
