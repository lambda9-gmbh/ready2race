import {ButtonBase, Box, Tooltip, useTheme} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {
    computeHourMarks,
    computeNowMarkerPercent,
    computeTimelinePositions,
    TimelineEntry,
    TimelineEntryState,
} from './timelineIndicator.ts'

type Props = {
    entries: TimelineEntry[]
    now: Date
    onEntryClick?: (id: string) => void
}

const ROW_HEIGHT = 22
const ROW_GAP = 3
// Beschriftungsstreifen unter der Fläche: Stundenmarken und das Uhrzeit-Label des Jetzt-Markers.
const AXIS_HEIGHT = 18

/**
 * Zentrierung eines Achsen-Labels auf seiner Marke — an den Rändern einseitig, damit "08:00" am
 * linken und "18:00" am rechten Ende nicht aus der Fläche hinausragen.
 */
const axisLabelTransform = (percent: number): string =>
    percent < 2 ? 'none' : percent > 98 ? 'translateX(-100%)' : 'translateX(-50%)'

/**
 * Compact "where are we right now" bar for one race day: every slot/match as a time-proportional
 * segment, color-coded by state, with a vertical now-marker. Shared between the Zeitplan tab
 * (fed from EventScheduleSlotDto) and the referee dashboard (fed from live-dashboard matches and
 * pending slots) - both map their own data shape to the generic TimelineEntry in
 * timelineIndicator.ts before handing it to this component.
 */
const ScheduleTimelineIndicator = ({entries, now, onEntryClick}: Props) => {
    const {t} = useTranslation()
    const theme = useTheme()

    if (entries.length === 0) {
        return null
    }

    const positioned = computeTimelinePositions(entries)
    const hourMarks = computeHourMarks(entries)
    const nowPercent = computeNowMarkerPercent(entries, now)
    const rows = Math.max(...positioned.map(e => e.stackRow)) + 1
    const barHeight = rows * ROW_HEIGHT + (rows - 1) * ROW_GAP

    const stateColor = (state: TimelineEntryState): string => {
        switch (state) {
            case 'finished':
                return theme.palette.success.main
            case 'running':
                return theme.palette.primary.main
            // Eigener Ton für den Lauf am Start, aber bewusst nicht `info.main`: den trägt schon
            // "wartet auf Beenden", und zwei gleich eingefärbte Balken nebeneinander wären keine
            // Unterscheidung mehr. Der helle Ton bleibt in derselben Farbfamilie wie der
            // "In Vorbereitung"-Chip (MUI-Farbe `info`).
            case 'preparing':
                return theme.palette.info.light
            // Eigener Ton, weder "läuft" noch "beendet": der Lauf ist gewertet, aber der
            // Beenden-Klick fehlt noch - auf dem Balken soll genau das auffallen.
            case 'awaitingFinish':
                return theme.palette.info.main
            case 'waiting':
                return theme.palette.warning.main
            case 'linked':
                return theme.palette.action.disabledBackground
            case 'skipped':
                return theme.palette.grey[400]
            case 'free':
            default:
                return theme.palette.grey[300]
        }
    }

    const stateLabel = (state: TimelineEntryState): string =>
        t(`event.schedule.indicator.state.${state}`)

    return (
        <Box
            sx={{
                position: 'relative',
                width: '100%',
                height: barHeight + AXIS_HEIGHT,
                overflow: 'visible',
                '@keyframes r2r-timeline-pulse': {
                    '0%': {boxShadow: `0 0 0 0 ${theme.palette.primary.main}80`},
                    '70%': {boxShadow: `0 0 0 5px ${theme.palette.primary.main}00`},
                    '100%': {boxShadow: `0 0 0 0 ${theme.palette.primary.main}00`},
                },
            }}
            aria-hidden={false}>
            {/* Die Fläche selbst — die Stundenmarken liegen IN ihr, die Beschriftung darunter. */}
            <Box
                sx={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    right: 0,
                    height: barHeight,
                    borderRadius: 1,
                    backgroundColor: 'action.hover',
                }}
            />
            {hourMarks.map(mark => (
                <Box key={mark.timeMs} sx={{pointerEvents: 'none'}}>
                    <Box
                        sx={{
                            position: 'absolute',
                            left: `${mark.percent}%`,
                            top: 0,
                            height: barHeight,
                            width: '1px',
                            backgroundColor: 'divider',
                        }}
                    />
                    <Box
                        sx={{
                            position: 'absolute',
                            left: `${mark.percent}%`,
                            top: barHeight + 2,
                            transform: axisLabelTransform(mark.percent),
                            fontSize: '0.65rem',
                            lineHeight: 1.2,
                            color: 'text.secondary',
                            whiteSpace: 'nowrap',
                        }}>
                        {format(new Date(mark.timeMs), t('format.time'))}
                    </Box>
                </Box>
            ))}
            {positioned.map(entry => (
                <Tooltip
                    key={entry.id}
                    title={`${entry.label} · ${format(new Date(entry.startTime), t('format.time'))} · ${stateLabel(entry.state)}`}>
                    <ButtonBase
                        onClick={() => onEntryClick?.(entry.id)}
                        aria-label={`${entry.label}, ${format(new Date(entry.startTime), t('format.time'))}, ${stateLabel(entry.state)}`}
                        sx={{
                            position: 'absolute',
                            left: `${entry.leftPercent}%`,
                            width: `${entry.widthPercent}%`,
                            top: entry.stackRow * (ROW_HEIGHT + ROW_GAP),
                            height: ROW_HEIGHT,
                            borderRadius: 0.75,
                            backgroundColor: entry.state === 'linked' ? 'transparent' : stateColor(entry.state),
                            border: entry.state === 'linked' ? `1px solid ${theme.palette.primary.main}` : 'none',
                            textDecoration: entry.state === 'skipped' ? 'line-through' : 'none',
                            opacity: entry.state === 'skipped' ? 0.6 : 1,
                            animation: entry.state === 'running' ? 'r2r-timeline-pulse 2s infinite' : 'none',
                            transition: 'filter 0.15s ease',
                            '&:hover': {
                                filter: 'brightness(0.92)',
                            },
                            '&:focus-visible': {
                                outline: `2px solid ${theme.palette.primary.dark}`,
                                outlineOffset: 1,
                            },
                        }}
                    />
                </Tooltip>
            ))}
            {/* Der Jetzt-Marker: rote Linie über die volle Fläche plus Uhrzeit-Label im
                Achsenstreifen. Das Label bekommt einen Papier-Hintergrund und liegt über den
                Stundenmarken — kollidieren beide, gewinnt die Aussage "jetzt ist 09:12". */}
            {nowPercent != null && (
                <>
                    <Box
                        sx={{
                            position: 'absolute',
                            left: `${nowPercent}%`,
                            top: -2,
                            height: barHeight + 4,
                            width: '2px',
                            backgroundColor: 'error.main',
                            pointerEvents: 'none',
                            zIndex: 2,
                        }}
                    />
                    <Box
                        sx={{
                            position: 'absolute',
                            left: `${nowPercent}%`,
                            top: barHeight + 2,
                            transform: axisLabelTransform(nowPercent),
                            fontSize: '0.65rem',
                            lineHeight: 1.2,
                            fontWeight: 700,
                            color: 'error.main',
                            backgroundColor: 'background.paper',
                            borderRadius: 0.5,
                            px: 0.25,
                            whiteSpace: 'nowrap',
                            pointerEvents: 'none',
                            zIndex: 3,
                        }}
                        aria-label={t('event.schedule.indicator.now')}>
                        {format(now, t('format.time'))}
                    </Box>
                </>
            )}
        </Box>
    )
}

export default ScheduleTimelineIndicator
