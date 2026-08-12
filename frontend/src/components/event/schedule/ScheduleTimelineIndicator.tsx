import {alpha, ButtonBase, Box, Stack, Tooltip, Typography, useTheme} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {useEffect, useRef, useState} from 'react'
import {
    axisLabelAnchor,
    axisLabelPx,
    computeHourMarks,
    computeNowMarkerPercent,
    computeTimelinePositions,
    computeTimelineProjection,
    labelFitsWidth,
    nowLabelHidesHourLabel,
    NOW_LABEL_PX,
    PositionedTimelineEntry,
    TimelineAppearance,
    timelineEntryAppearance,
    TimelineEntry,
} from './timelineIndicator.ts'

type Props = {
    entries: TimelineEntry[]
    now: Date
    onEntryClick?: (id: string) => void
    /**
     * Zeitplan-Tab rendert 'full' (Standard), das Schiedsrichter-Dashboard 'compact': dort ist
     * der Zeitstrahl Orientierung über den Karten, nicht die Hauptfläche — flachere Spuren und
     * keine Block-Kürzel, der Tooltip bleibt vollständig.
     */
    density?: 'full' | 'compact'
}

// Spurhöhe, Spurabstand und Höhe des Beschriftungsstreifens (Stundenmarken + Jetzt-Label) je
// Dichte-Stufe.
const SIZES = {
    full: {rowHeight: 22, rowGap: 3, axisHeight: 18},
    compact: {rowHeight: 14, rowGap: 2, axisHeight: 16},
} as const

/**
 * Der Anker eines Achsen-Labels (siehe axisLabelAnchor) als CSS-Transform: mittig auf der Marke,
 * an den Rändern nach innen geklappt, damit nichts aus der Fläche clippt.
 */
const ANCHOR_TRANSFORM: Record<ReturnType<typeof axisLabelAnchor>, string> = {
    start: 'none',
    center: 'translateX(-50%)',
    end: 'translateX(-100%)',
}

/**
 * Compact "where are we right now" bar for one race day: every slot/match as a time-proportional
 * segment, color-coded by state, with a vertical now-marker. Shared between the Zeitplan tab
 * (fed from EventScheduleSlotDto) and the referee dashboard (fed from live-dashboard matches and
 * pending slots) - both map their own data shape to the generic TimelineEntry in
 * timelineIndicator.ts before handing it to this component.
 */
const ScheduleTimelineIndicator = ({entries, now, onEntryClick, density = 'full'}: Props) => {
    const {t} = useTranslation()
    const theme = useTheme()
    const {rowHeight, rowGap, axisHeight} = SIZES[density]

    // Breite der Fläche in Pixeln, für die Frage "passt das Kürzel in diesen Block?" —
    // beobachtet statt einmalig gemessen, damit Fenstergrößen-Änderungen die Kürzel nachziehen.
    const containerRef = useRef<HTMLDivElement | null>(null)
    const [containerWidth, setContainerWidth] = useState(0)
    useEffect(() => {
        const el = containerRef.current
        if (el == null) {
            return
        }
        const observer = new ResizeObserver(observed =>
            setContainerWidth(observed[0]?.contentRect.width ?? 0),
        )
        observer.observe(el)
        return () => observer.disconnect()
    }, [])

    if (entries.length === 0) {
        return null
    }

    const positioned = computeTimelinePositions(entries)
    // Die Marken werden gegen die gemessene Breite geplant (Format und Schrittweite, siehe
    // computeHourMarks) — auf dem Handy nackte Stunden statt ineinanderlaufender "08:00"-Labels.
    const hourMarkPlan = computeHourMarks(entries, containerWidth)
    const markLabelPx = axisLabelPx(hourMarkPlan.format)
    const nowPercent = computeNowMarkerPercent(entries, now)
    // Soll vs. Ist: Ist-Start-Striche und die halbtransparente Erwartungs-Andeutung (siehe
    // computeTimelineProjection — dort steht auch, warum das nur eine Andeutung sein darf).
    const projection = computeTimelineProjection(entries, now)
    const rows = Math.max(...positioned.map(e => e.stackRow)) + 1
    const barHeight = rows * rowHeight + (rows - 1) * rowGap

    // Die Farbfamilien der Status-Chips (matchStatusChip.ChipColor), übersetzt in die Theme-
    // Palette. 'default' hat dort keine eigene Palette — grau in drei Stufen übernimmt die Rolle.
    const chipPalette = (
        color: TimelineAppearance['color'],
    ): {main: string; light: string; dark: string} =>
        color === 'default'
            ? {
                  main: theme.palette.grey[500],
                  light: theme.palette.grey[400],
                  dark: theme.palette.grey[700],
              }
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

    /**
     * Der Tooltip eines Blocks: volle Bezeichnung, Runde/Lauf, geplante Zeit (mit Dauer, wenn
     * gepflegt), Ist-Start (wenn gestartet) und Status — die Langform dessen, wofür der Block
     * selbst nur Platz für ein Kürzel hat.
     */
    const entryTooltip = (entry: PositionedTimelineEntry) => (
        <Stack spacing={0.25}>
            <Typography variant={'caption'} fontWeight={600}>
                {entry.label}
            </Typography>
            {entry.roundLabel != null && entry.roundLabel !== entry.label && (
                <Typography variant={'caption'}>{entry.roundLabel}</Typography>
            )}
            <Typography variant={'caption'}>
                {t('event.schedule.indicator.planned', {
                    time: format(new Date(entry.startTime), t('format.time')),
                })}
                {entry.durationMinutes ? ` · ${entry.durationMinutes} min` : ''}
            </Typography>
            {entry.actualStartTime != null && (
                <Typography variant={'caption'}>
                    {t('event.schedule.indicator.started', {
                        time: format(new Date(entry.actualStartTime), t('format.time')),
                    })}
                </Typography>
            )}
            {/* Die Erwartung trägt die Tilde aus gutem Grund: Sie ist die pauschale Verschiebung
                um die aktuelle Verspätung, keine Prognose je Lauf (computeTimelineProjection). */}
            {projection.expected.has(entry.id) && (
                <Typography variant={'caption'}>
                    {t('event.schedule.indicator.expected', {
                        time: format(
                            new Date(projection.expected.get(entry.id)!.expectedStartMs),
                            t('format.time'),
                        ),
                    })}
                </Typography>
            )}
            {/* Kein color-Prop: der Tooltip bringt seine eigene (helle) Textfarbe mit. */}
            <Typography variant={'caption'} sx={{opacity: 0.85}}>
                {stateLabel(entry)}
            </Typography>
        </Stack>
    )

    return (
        <Box
            ref={containerRef}
            sx={{
                position: 'relative',
                width: '100%',
                height: barHeight + axisHeight,
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
            {hourMarkPlan.marks.map(mark => (
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
                    {/* Läge das Stunden-Label unter dem Jetzt-Label, blieben von "22:00" nur
                        angeschnittene Ziffern übrig — dann lieber gar keins, "jetzt" gewinnt
                        (in Pixeln gerechnet, damit das auch auf schmalen Flächen greift). */}
                    {(nowPercent == null ||
                        !nowLabelHidesHourLabel(
                            mark.percent,
                            nowPercent,
                            markLabelPx,
                            containerWidth,
                        )) && (
                        <Box
                            sx={{
                                position: 'absolute',
                                left: `${mark.percent}%`,
                                top: barHeight + 2,
                                transform:
                                    ANCHOR_TRANSFORM[
                                        axisLabelAnchor(mark.percent, markLabelPx, containerWidth)
                                    ],
                                fontSize: '0.65rem',
                                lineHeight: 1.2,
                                color: 'text.secondary',
                                whiteSpace: 'nowrap',
                            }}>
                            {hourMarkPlan.format === 'hour'
                                ? String(new Date(mark.timeMs).getHours())
                                : format(new Date(mark.timeMs), t('format.time'))}
                        </Box>
                    )}
                </Box>
            ))}
            {positioned.map(entry => {
                const a = timelineEntryAppearance(entry.state, entry.bye)
                const pal = chipPalette(a.color)
                const blockPx = (containerWidth * entry.widthPercent) / 100
                const shortLabel = entry.shortLabel ?? ''
                // Passt das Kürzel nicht, bleibt der Block leer — der Tooltip sagt alles.
                // Kompakt gibt es gar keine Kürzel: die flachen Spuren des Dashboards sind
                // Orientierung, keine Lesefläche.
                const showLabel = density === 'full' && labelFitsWidth(shortLabel, blockPx)
                const laneTop = entry.stackRow * (rowHeight + rowGap)
                const actualLeft = projection.actualLeftPercent.get(entry.id)
                const expected = projection.expected.get(entry.id)
                return (
                    <Box key={entry.id} component={'span'}>
                        {/* Die Andeutung, wo der Eintrag angesichts der aktuellen Verspätung zu
                            erwarten ist — halbtransparent und nicht klickbar, die Zahl dazu
                            steht (mit Tilde) im Tooltip des Plan-Blocks. */}
                        {expected != null && (
                            <Box
                                sx={{
                                    position: 'absolute',
                                    left: `${expected.leftPercent}%`,
                                    width: `${Math.min(entry.widthPercent, 100 - expected.leftPercent)}%`,
                                    top: laneTop,
                                    height: rowHeight,
                                    borderRadius: 0.75,
                                    backgroundColor: alpha(pal.main, 0.25),
                                    border: `1px dashed ${alpha(pal.main, 0.6)}`,
                                    pointerEvents: 'none',
                                }}
                            />
                        )}
                        <Tooltip title={entryTooltip(entry)}>
                            <ButtonBase
                                onClick={() => onEntryClick?.(entry.id)}
                                aria-label={`${entry.label}, ${format(new Date(entry.startTime), t('format.time'))}, ${stateLabel(entry)}`}
                                sx={{
                                    position: 'absolute',
                                    left: `${entry.leftPercent}%`,
                                    width: `${entry.widthPercent}%`,
                                    top: entry.stackRow * (rowHeight + rowGap),
                                    height: rowHeight,
                                    borderRadius: 0.75,
                                    overflow: 'hidden',
                                    ...segmentSx(a),
                                    animation:
                                        entry.state === 'running'
                                            ? 'r2r-timeline-pulse 2s infinite'
                                            : 'none',
                                    transition: 'filter 0.15s ease',
                                    '&:hover': {
                                        filter: 'brightness(0.92)',
                                    },
                                    '&:focus-visible': {
                                        outline: `2px solid ${theme.palette.primary.dark}`,
                                        outlineOffset: 1,
                                    },
                                }}>
                                {showLabel && (
                                    <Box
                                        component={'span'}
                                        sx={{
                                            fontSize: '0.65rem',
                                            lineHeight: 1,
                                            px: 0.5,
                                            whiteSpace: 'nowrap',
                                            overflow: 'hidden',
                                            // Auf Füllungen die Kontrastfarbe der Palette, auf
                                            // Umrissen die normale Textfarbe der Fläche.
                                            color:
                                                a.variant === 'outlined'
                                                    ? 'text.primary'
                                                    : theme.palette.getContrastText(
                                                          a.muted ? pal.light : pal.main,
                                                      ),
                                        }}>
                                        {shortLabel}
                                    </Box>
                                )}
                            </ButtonBase>
                        </Tooltip>
                        {/* Die Ist-Ebene: dünner Strich am unteren Rand der Spur, positioniert am
                            tatsächlichen Start. Deckt er sich mit der Blockkante, wurde pünktlich
                            gestartet — steht er daneben, ist GENAU DAS die Aussage. */}
                        {actualLeft != null && (
                            <Box
                                sx={{
                                    position: 'absolute',
                                    left: `${actualLeft}%`,
                                    width: `${Math.min(entry.widthPercent, 100 - actualLeft)}%`,
                                    top: laneTop + rowHeight - 3,
                                    height: 3,
                                    borderRadius: 0.5,
                                    backgroundColor: pal.dark,
                                    pointerEvents: 'none',
                                    zIndex: 1,
                                }}
                            />
                        )}
                    </Box>
                )
            })}
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
                            // Auch das Jetzt-Label klappt am Rand nach innen statt zu clippen.
                            transform:
                                ANCHOR_TRANSFORM[
                                    axisLabelAnchor(nowPercent, NOW_LABEL_PX, containerWidth)
                                ],
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
