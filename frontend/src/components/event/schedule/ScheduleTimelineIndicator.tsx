import {alpha, ButtonBase, Box, Tooltip, useTheme} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {
    computeHourMarks,
    computeNowMarkerPercent,
    computeTimelinePositions,
    TimelineAppearance,
    timelineEntryAppearance,
    TimelineEntry,
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

    // Die Farbfamilien der Status-Chips (matchStatusChip.ChipColor), übersetzt in die Theme-
    // Palette. 'default' hat dort keine eigene Palette — grau in zwei Stufen übernimmt die Rolle.
    const chipPalette = (color: TimelineAppearance['color']): {main: string; light: string} =>
        color === 'default'
            ? {main: theme.palette.grey[500], light: theme.palette.grey[400]}
            : theme.palette[color]

    /**
     * Ein {@link TimelineAppearance}-Datensatz als sx-Eigenschaften: gefüllt oder als Umriss,
     * gedämpft über die helle Palettenstufe, die Schraffur als halbtransparente Papier-Streifen
     * über der Füllung (funktioniert damit auf jeder Farbe und in jedem Theme).
     */
    const segmentSx = (a: TimelineAppearance) => {
        const pal = chipPalette(a.color)
        const fill = a.muted ? pal.light : pal.main
        return {
            backgroundColor: a.variant === 'outlined' ? 'transparent' : fill,
            backgroundImage: a.hatched
                ? `repeating-linear-gradient(45deg, ${alpha(theme.palette.background.paper, 0.55)} 0 4px, transparent 4px 9px)`
                : 'none',
            border:
                a.variant === 'outlined'
                    ? `1px ${a.dashed ? 'dashed' : 'solid'} ${theme.palette.text.secondary}`
                    : 'none',
            textDecoration: a.strikeThrough ? 'line-through' : 'none',
            opacity: a.strikeThrough ? 0.65 : 1,
        }
    }

    // Dieselbe Vorrangregel wie timelineEntryAppearance: ein aktiviertes/fahrendes Freilos
    // spricht wie ein normaler Lauf, alle anderen Freilose sagen, was sie sind.
    const stateLabel = (entry: TimelineEntry): string =>
        entry.bye && entry.state !== 'running' && entry.state !== 'preparing'
            ? t('event.schedule.indicator.state.bye')
            : t(`event.schedule.indicator.state.${entry.state}`)

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
                    title={`${entry.label} · ${format(new Date(entry.startTime), t('format.time'))} · ${stateLabel(entry)}`}>
                    <ButtonBase
                        onClick={() => onEntryClick?.(entry.id)}
                        aria-label={`${entry.label}, ${format(new Date(entry.startTime), t('format.time'))}, ${stateLabel(entry)}`}
                        sx={{
                            position: 'absolute',
                            left: `${entry.leftPercent}%`,
                            width: `${entry.widthPercent}%`,
                            top: entry.stackRow * (ROW_HEIGHT + ROW_GAP),
                            height: ROW_HEIGHT,
                            borderRadius: 0.75,
                            ...segmentSx(timelineEntryAppearance(entry.state, entry.bye)),
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
