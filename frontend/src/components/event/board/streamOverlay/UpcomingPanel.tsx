import {Stack, Typography, useTheme} from '@mui/material'
import {format} from 'date-fns'
import {useTranslation} from 'react-i18next'
import {AthleteBoardMatch, BoardElement} from '@api/types.gen.ts'
import FitToHeight from './FitToHeight.tsx'
import FlipList from '../FlipList.tsx'
import StreamBoatRow from './StreamBoatRow.tsx'
import StreamPanelShell from './StreamPanelShell.tsx'
import {
    byNullsLast,
    competitionLabel,
    formatCountdownClock,
    roundMatchLabel,
    solidOr,
    streamNameForms,
} from './streamDisplay.ts'
import useTicker from './useTicker.ts'

interface UpcomingPanelProps {
    match: AthleteBoardMatch
    element: BoardElement
    clockOffsetMs: number
}

const COUNTDOWN_TICK_MS = 1000

/**
 * „Als Nächstes": zentriertes TV-Grafik-Panel mit Startzeit, optionalem Countdown
 * (`showCountdown`, Voreinstellung an) und optionaler Weiterkommens-Regel
 * (`showAdvancement`, Voreinstellung aus — der Server liefert die dafür nötigen Felder
 * nur bei Anforderung, siehe `AthleteBoardMatch.nextRoundName`/`advancingSeats`).
 */
const UpcomingPanel = ({match, element, clockOffsetMs}: UpcomingPanelProps) => {
    const {t} = useTranslation()
    const theme = useTheme()
    const names = streamNameForms(element)
    const streamCrew = element.streamCrew ?? 'CLUBS_FIRST'
    // Aufstellung trägt nie Teilergebnisse (place/timeString sind im Upcoming-Block
    // immer null) — Startnummern-Reihenfolge reicht, aber deterministisch statt "wie
    // die Antwort sie zufällig liefert".
    const teams = [...match.teams].sort(byNullsLast(team => team.startNumber))

    const showCountdown = element.showCountdown !== false && match.startTime != null
    const now = useTicker(COUNTDOWN_TICK_MS, showCountdown)
    const startMs = match.startTime ? Date.parse(match.startTime) : null
    const remainingMs = showCountdown && startMs != null ? startMs - (now - clockOffsetMs) : null

    return (
        <StreamPanelShell
            panelKey={match.matchId}
            stateLabel={t('event.boards.stream.upcoming')}
            title={competitionLabel(
                match.competitionName,
                match.competitionShortName,
                names.competitions,
            )}
            roundLine={roundMatchLabel(match.roundName, match.matchName)}
            headerTrailing={
                match.startTime ? (
                    <Typography
                        variant="h4"
                        sx={{fontWeight: 700, flexShrink: 0, fontVariantNumeric: 'tabular-nums'}}>
                        {format(new Date(match.startTime), t('format.time'))}
                    </Typography>
                ) : undefined
            }>
            {remainingMs != null && (
                <Typography
                    variant="h5"
                    sx={{
                        fontVariantNumeric: 'tabular-nums',
                        fontWeight: 600,
                        flexShrink: 0,
                        color: solidOr(theme.palette.primary.light, '#64b5f6'),
                    }}>
                    {t('event.boards.stream.inMinutes', {time: formatCountdownClock(remainingMs)})}
                </Typography>
            )}
            {/* „Weiter kommen N Boote → …" — dieselbe Formulierung wie die Sprecher-Kachel
                und die Athleten-Anzeige, Suchanker `nextRoundName`/`advancingSeats`. */}
            {element.showAdvancement === true && match.nextRoundName && (
                <Typography
                    variant="body1"
                    sx={{
                        fontWeight: 600,
                        flexShrink: 0,
                        color: solidOr(theme.palette.primary.light, '#64b5f6'),
                    }}>
                    {match.advancingSeats != null
                        ? t('event.info.athleteBoard.advancing', {
                              count: match.advancingSeats,
                              round: match.nextRoundName,
                          })
                        : t('event.info.athleteBoard.advancingUnsized', {
                              round: match.nextRoundName,
                          })}
                </Typography>
            )}
            {/* Keine Bildlaufleiste auf einer TV-Grafik — eine Kachel scrollt nie; passt die
                Aufstellung nicht in die Panelhöhe, verkleinert FitToHeight sie. */}
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
                                size="large"
                            />
                        )}
                    />
                </Stack>
            </FitToHeight>
        </StreamPanelShell>
    )
}

export default UpcomingPanel
