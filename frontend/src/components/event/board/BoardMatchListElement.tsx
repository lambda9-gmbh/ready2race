import {Box, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {BoardElement, BoardViewDto} from '@api/types.gen'
import {formatClockTime, formatPlace, scaled, teamLabel} from '../info/athleteBoard/common'
import {listForElement, programForElement} from './boardView'

interface BoardMatchListElementProps {
    element: BoardElement
    view: BoardViewDto
}

/**
 * Ein Listen-Element: die kompakte Zeilenform der alten Info-Views. Bewusst schlicht —
 * Zeit, Wettkampf, Runde/Lauf, bei Ergebnissen zusätzlich der Sieger. Wer Boote und
 * Besatzungen sehen will, legt ein Lauf-Element daneben.
 */
const BoardMatchListElement = ({element, view}: BoardMatchListElementProps) => {
    const {t} = useTranslation()

    const list = listForElement(view, element)
    const program = programForElement(view, element)

    const title =
        element.listMode === 'RESULTS'
            ? t('event.boards.element.listMode.results')
            : element.listMode === 'RUNNING'
              ? t('event.boards.element.listMode.running')
              : element.listMode === 'SCHEDULE'
                ? t('event.boards.element.listMode.schedule')
                : t('event.boards.element.listMode.upcoming')

    // Kurzform: das Wettkampf-Kürzel (short_name) statt des vollen Namens — für schmale
    // Kacheln, in denen "Mixed-Doppelvierer mit Steuerfrau/-mann" jede Zeile sprengt.
    // Ohne gepflegtes Kürzel bleibt der volle Name stehen, eine leere Zeile wäre schlimmer.
    const competitionLabel = (name: string, shortName?: string | null) =>
        element.useShortNames === true ? (shortName ?? name) : name

    const rows: {key: string; time: string | null; label: string; detail: string | null; state?: string}[] =
        program != null
            ? program.map((entry, index) => ({
                  key: `${entry.startTime ?? ''}-${index}`,
                  time: entry.startTime ? formatClockTime(entry.startTime) : null,
                  label:
                      entry.name ??
                      [
                          competitionLabel(entry.competitionName ?? '', entry.competitionShortName),
                          entry.roundName,
                          entry.matchName,
                      ]
                          .filter(Boolean)
                          .join(' · '),
                  detail: null,
                  state: entry.state,
              }))
            : list == null
              ? []
              : list.mode === 'RESULTS'
              ? list.results.map(result => {
                    const winner = result.teams.find(team => team.place === 1)
                    return {
                        key: result.matchId,
                        time: result.startTime ? formatClockTime(result.startTime) : null,
                        label: [
                            competitionLabel(result.competitionName, result.competitionShortName),
                            result.roundName,
                            result.matchName,
                        ]
                            .filter(Boolean)
                            .join(' · '),
                        detail: winner
                            ? `${formatPlace(1, t)} ${teamLabel(winner, t, 'short')}${winner.timeString ? ` — ${winner.timeString}` : ''}`
                            : null,
                    }
                })
              : list.matches.map(match => ({
                    key: match.matchId,
                    time: match.startTime ? formatClockTime(match.startTime) : null,
                    label:
                        match.name ??
                        [
                            competitionLabel(match.competitionName, match.competitionShortName),
                            match.roundName,
                            match.matchName,
                        ]
                            .filter(Boolean)
                            .join(' · '),
                    detail: null,
                }))

    return (
        <Box
            sx={{
                height: '100%',
                minHeight: 0,
                display: 'grid',
                gridTemplateRows: 'auto minmax(0, 1fr)',
                rowGap: scaled('0.25rem', '0.4vw', '0.6rem'),
                p: scaled('0.5rem', '0.9vw', '1.25rem'),
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
            }}>
            <Typography
                sx={{
                    fontSize: scaled('0.75rem', '1vw', '1.8rem'),
                    fontWeight: 700,
                    textTransform: 'uppercase',
                    letterSpacing: '0.04em',
                }}
                color="text.secondary">
                {title}
            </Typography>
            <Box sx={{minHeight: 0, overflow: 'hidden'}}>
                {rows.length === 0 ? (
                    <Typography
                        sx={{fontSize: scaled('0.85rem', '1.2vw', '1.8rem')}}
                        color="text.secondary">
                        {element.listMode === 'RESULTS'
                            ? t('event.boards.element.emptyPast')
                            : element.listMode === 'RUNNING'
                              ? t('event.boards.element.emptyCurrent')
                              : element.listMode === 'SCHEDULE'
                                ? t('event.boards.element.emptySchedule')
                                : t('event.boards.element.emptyUpcoming')}
                    </Typography>
                ) : (
                    rows.map((row, index) => (
                        <Stack
                            key={row.key}
                            direction="row"
                            gap={1.5}
                            alignItems="baseline"
                            sx={{
                                borderTop: index > 0 ? '1px solid' : 'none',
                                borderColor: 'divider',
                                py: scaled('0.15rem', '0.25vw', '0.4rem'),
                                minWidth: 0,
                            }}>
                            <Typography
                                sx={{
                                    fontSize: scaled('0.9rem', '1.4vw', '2rem'),
                                    fontWeight: 700,
                                    flexShrink: 0,
                                    minWidth: '3.2em',
                                    // Tagesprogramm: Beendetes tritt zurück, Laufendes leuchtet.
                                    opacity: row.state === 'FINISHED' ? 0.45 : 1,
                                    color: row.state === 'RUNNING' ? 'primary.main' : undefined,
                                }}>
                                {row.time ?? '–'}
                            </Typography>
                            <Box sx={{minWidth: 0}}>
                                <Typography
                                    noWrap
                                    sx={{
                                        fontSize: scaled('0.9rem', '1.4vw', '2rem'),
                                        fontWeight: row.state === 'RUNNING' ? 800 : 600,
                                        opacity: row.state === 'FINISHED' ? 0.45 : 1,
                                    }}
                                    color={row.state === 'RUNNING' ? 'primary.main' : undefined}>
                                    {row.state === 'FINISHED' ? '✓ ' : row.state === 'RUNNING' ? '▶ ' : ''}
                                    {row.label}
                                </Typography>
                                {row.detail && (
                                    <Typography
                                        noWrap
                                        sx={{fontSize: scaled('0.7rem', '1.1vw', '1.5rem')}}
                                        color="text.secondary">
                                        {row.detail}
                                    </Typography>
                                )}
                            </Box>
                        </Stack>
                    ))
                )}
            </Box>
        </Box>
    )
}

export default BoardMatchListElement
