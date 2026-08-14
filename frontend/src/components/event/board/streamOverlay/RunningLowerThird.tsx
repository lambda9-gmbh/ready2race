import {Box, Stack, Typography, useTheme} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardMatch, BoardElement} from '@api/types.gen.ts'
import {sortRunningTeams} from '../../info/athleteBoard/common'
import FlipList from '../FlipList.tsx'
import StreamBoatRow from './StreamBoatRow.tsx'
import StreamClockLabel from './StreamClockLabel.tsx'
import StreamStateBadge from './StreamStateBadge.tsx'
import {competitionLabel, roundMatchLabel, solidOr} from './streamDisplay.ts'

interface RunningLowerThirdProps {
    match: AthleteBoardMatch
    element: BoardElement
    clockOffsetMs: number
}

/**
 * „Läuft": Lower-Third am unteren Rand, maximal gut ein Drittel der Höhe. Die
 * Bootszeilen sortieren sich per FlipList um, sobald Teilergebnisse eintreffen
 * (`sortRunningTeams` — gewertete Boote nach Platz oben, ungewertete nach Startnummer
 * darunter, DNF/DQ ans Ende; dieselbe Regel wie die Athleten-Anzeige).
 */
const RunningLowerThird = ({match, element, clockOffsetMs}: RunningLowerThirdProps) => {
    const {t} = useTranslation()
    const theme = useTheme()
    const useShortNames = element.useShortNames !== false
    const streamCrew = element.streamCrew ?? 'CLUBS_FIRST'
    const roundLine = roundMatchLabel(match.roundName, match.matchName)
    const teams = sortRunningTeams(match.teams)

    return (
        <Box sx={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'flex-end'}}>
            <Box
                sx={{
                    m: 3,
                    width: 1,
                    maxHeight: '38vh',
                    overflow: 'hidden',
                    borderRadius: 2,
                    display: 'flex',
                    backgroundColor: solidOr(theme.palette.text.primary, '#1d1d1d'), // dunkles, DECKENDES Panel
                    color: solidOr(theme.palette.background.paper, '#ffffff'),
                }}>
                {/* Akzentband links — eine harte Kante in Primärfarbe, kein Verlauf. */}
                <Box
                    sx={{
                        width: '0.6rem',
                        flexShrink: 0,
                        backgroundColor: solidOr(theme.palette.primary.main, '#1976d2'),
                    }}
                />
                <Stack sx={{minWidth: 0, flex: 1, p: 3, gap: 1.5, overflow: 'hidden'}}>
                    {/* Kopfzeile: Zustand + Wettkampf + Laufuhr (rechtsbündig). flexShrink 0,
                        weil der Spalten-Flex unter maxHeight sonst Kopf und Unterzeile
                        zusammendrückt (noWrap setzt overflow hidden und gibt damit die
                        min-height frei) — die Zeilen ragten dann optisch in die Unterzeile. */}
                    <Stack direction="row" alignItems="center" gap={2} sx={{flexShrink: 0}}>
                        <StreamStateBadge label={t('event.boards.stream.running')} indicator />
                        <Typography variant="h4" noWrap sx={{fontWeight: 700, minWidth: 0, flex: 1}}>
                            {competitionLabel(match.competitionName, match.competitionShortName, useShortNames)}
                        </Typography>
                        <StreamClockLabel match={match} clockOffsetMs={clockOffsetMs} />
                    </Stack>
                    {roundLine && (
                        <Typography variant="h6" noWrap sx={{fontWeight: 500, flexShrink: 0}}>
                            {roundLine}
                        </Typography>
                    )}

                    {/* Je Boot eine Zeile — Positionswechsel animieren via FlipList. Nur
                        dieser Block darf schrumpfen (minHeight 0), Kopf und Unterzeile nie.
                        Ab fünf Booten weichen die Rundenzeilen — sonst schnitte die
                        38-vh-Kante auch bei Full HD mitten durch die letzte Zeile. */}
                    <Stack sx={{gap: 1, overflow: 'hidden', minHeight: 0, flex: '1 1 auto'}}>
                        <FlipList
                            items={teams}
                            keyOf={team => String(team.startNumber)}
                            render={team => (
                                <StreamBoatRow
                                    team={team}
                                    crewMode={streamCrew}
                                    useShortNames={useShortNames}
                                    failedFallback={t('event.info.athleteBoard.failed')}
                                    size="compact"
                                    showLaps={teams.length <= 4}
                                />
                            )}
                        />
                    </Stack>
                </Stack>
            </Box>
        </Box>
    )
}

export default RunningLowerThird
