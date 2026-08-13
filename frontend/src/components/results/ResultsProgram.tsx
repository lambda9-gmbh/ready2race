import {useTranslation} from 'react-i18next'
import {Alert, Box, Chip, Divider, Stack, Typography} from '@mui/material'
import Throbber from '@components/Throbber.tsx'
import {BoardProgramEntry} from '@api/types.gen.ts'
import {getPublicProgram} from '@api/sdk.gen.ts'
import {usePolledFetch} from '@utils/usePolledFetch.ts'
import {format} from 'date-fns'

type Props = {
    eventId: string
}

/**
 * Etwas gemächlicher als der Live-Tab (15 s): Der Zeitplan ändert sich nur, wenn Läufe starten,
 * enden oder verschoben werden — eine halbe Minute Verzug fällt hier nicht auf.
 */
const REFRESH_MS = 30_000

/**
 * Der Tab „Zeitplan" der öffentlichen Ergebnisanzeige: das ganze Tagesprogramm aus dem
 * Zeitplan — auch das bereits Gefahrene, im Unterschied zum Live-Tab, der nur Laufendes und
 * Anstehendes zeigt. Die Einträge kommen aus demselben Server-Bausatz wie die SCHEDULE-Boards
 * (`buildProgram`) und tragen bewusst weder Aufstellungen noch Ergebnisse.
 *
 * Gestaltet als kompakte Liste statt Karten: Besucher überfliegen das Programm wie einen
 * Aushang — Uhrzeit, Name, Zustand, eine Zeile pro Eintrag, gruppiert nach Tag.
 */
const ResultsProgram = ({eventId}: Props) => {
    const {t} = useTranslation()

    const {data, initialLoad, failed, lastUpdated} = usePolledFetch<BoardProgramEntry[]>(
        async signal => {
            const {data} = await getPublicProgram({signal, path: {eventId}})
            return data ?? null
        },
        REFRESH_MS,
        [eventId],
    )

    if (initialLoad) {
        return (
            <Stack sx={{alignItems: 'center', p: 2}}>
                <Throbber />
            </Stack>
        )
    }

    if (data === null) {
        return (
            <Box sx={{p: 2}}>
                <Alert severity={'warning'}>{t('results.program.loadFailed')}</Alert>
            </Box>
        )
    }

    if (data.length === 0) {
        return (
            <Box sx={{p: 2}}>
                <Alert severity={'info'}>{t('results.program.empty')}</Alert>
            </Box>
        )
    }

    // Gruppierung nach Kalendertag: bei einer Regatta über mehrere Tage trennt eine
    // Tagesüberschrift die Blöcke. Einträge ohne Zeit sammeln sich hinten.
    const days = data.reduce<{day: string | null; entries: BoardProgramEntry[]}[]>(
        (groups, entry) => {
            const day = entry.startTime ? format(new Date(entry.startTime), 'yyyy-MM-dd') : null
            const last = groups[groups.length - 1]
            if (last && last.day === day) {
                last.entries.push(entry)
            } else {
                groups.push({day, entries: [entry]})
            }
            return groups
        },
        [],
    )

    const stateChip = (entry: BoardProgramEntry) => {
        // Programmpunkte (Pausen) tragen keinen Laufzustand — ein "Anstehend" an der
        // Mittagspause wäre nur Rauschen.
        if (entry.name != null && entry.state === 'UPCOMING') {
            return null
        }
        switch (entry.state) {
            case 'RUNNING':
                return (
                    <Chip size={'small'} color={'info'} label={t('results.program.running')} />
                )
            case 'FINISHED':
                return <Chip size={'small'} label={t('results.program.finished')} />
            default:
                return (
                    <Chip
                        size={'small'}
                        variant={'outlined'}
                        label={t('results.program.upcoming')}
                    />
                )
        }
    }

    return (
        <Stack spacing={1} sx={{p: 2}}>
            {failed && lastUpdated && (
                <Typography variant={'body2'} color={'text.secondary'}>
                    {t('results.liveMatches.stale', {
                        time: format(lastUpdated, t('format.time')),
                    })}
                </Typography>
            )}
            {days.map((group, groupIndex) => (
                <Stack key={group.day ?? `ohne-tag-${groupIndex}`} spacing={0}>
                    {group.day && (
                        <Typography variant={'subtitle1'} fontWeight={'bold'} sx={{mt: 1}}>
                            {format(new Date(group.entries[0].startTime!), t('format.date'))}
                        </Typography>
                    )}
                    {group.entries.map((entry, index) => (
                        <Box key={index}>
                            {index > 0 && <Divider />}
                            <Box
                                sx={{
                                    display: 'flex',
                                    alignItems: 'baseline',
                                    columnGap: 1.5,
                                    py: 0.75,
                                }}>
                                <Typography
                                    sx={{
                                        fontVariantNumeric: 'tabular-nums',
                                        flexShrink: 0,
                                        color: 'text.secondary',
                                    }}>
                                    {entry.startTime
                                        ? format(new Date(entry.startTime), t('format.time'))
                                        : '–'}
                                </Typography>
                                <Box sx={{minWidth: 0, flex: 1}}>
                                    {entry.name != null ? (
                                        // Programmpunkt (Pause o.ä.): nur der Name, kursiv.
                                        <Typography fontStyle={'italic'}>{entry.name}</Typography>
                                    ) : (
                                        <>
                                            <Typography sx={{overflowWrap: 'anywhere'}}>
                                                {entry.competitionName}
                                            </Typography>
                                            <Typography
                                                variant={'body2'}
                                                color={'text.secondary'}>
                                                {[entry.roundName, entry.matchName]
                                                    .filter(
                                                        (part, i, parts) =>
                                                            part && part !== parts[i - 1],
                                                    )
                                                    .join(' · ')}
                                            </Typography>
                                        </>
                                    )}
                                </Box>
                                <Box sx={{flexShrink: 0, alignSelf: 'center'}}>
                                    {stateChip(entry)}
                                </Box>
                            </Box>
                        </Box>
                    ))}
                </Stack>
            ))}
        </Stack>
    )
}

export default ResultsProgram
