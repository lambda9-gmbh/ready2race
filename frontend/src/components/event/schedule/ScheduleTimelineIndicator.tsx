import {ButtonBase, Box, Tooltip, useTheme} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {
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
    const nowPercent = computeNowMarkerPercent(entries, now)
    const rows = Math.max(...positioned.map(e => e.stackRow)) + 1
    const barHeight = rows * ROW_HEIGHT + (rows - 1) * ROW_GAP

    const stateColor = (state: TimelineEntryState): string => {
        switch (state) {
            case 'finished':
                return theme.palette.success.main
            case 'running':
                return theme.palette.primary.main
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
                height: barHeight,
                borderRadius: 1,
                backgroundColor: 'action.hover',
                overflow: 'visible',
                '@keyframes r2r-timeline-pulse': {
                    '0%': {boxShadow: `0 0 0 0 ${theme.palette.primary.main}80`},
                    '70%': {boxShadow: `0 0 0 5px ${theme.palette.primary.main}00`},
                    '100%': {boxShadow: `0 0 0 0 ${theme.palette.primary.main}00`},
                },
            }}
            aria-hidden={false}>
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
            {nowPercent != null && (
                <Tooltip title={t('event.schedule.indicator.now')}>
                    <Box
                        sx={{
                            position: 'absolute',
                            left: `${nowPercent}%`,
                            top: -2,
                            bottom: -2,
                            width: '2px',
                            backgroundColor: 'text.primary',
                            pointerEvents: 'none',
                        }}
                    />
                </Tooltip>
            )}
        </Box>
    )
}

export default ScheduleTimelineIndicator
