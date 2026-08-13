import {Box, Stack, Typography, useTheme} from '@mui/material'
import {format} from 'date-fns'
import {useTranslation} from 'react-i18next'
import {
    AthleteBoardTeam,
    AthleteBoardResultTeam,
    BoardElement,
    BoardViewDto,
} from '@api/types.gen.ts'
import {streamOverlayContent, STREAM_DEFAULT_BACKGROUND} from './streamOverlay.ts'
import {failedLabel} from '@utils/matchResultStatus.ts'

type Props = {
    view: BoardViewDto
    element: BoardElement
}

/** Die Boot-Felder, die das Overlay zeigt — laufende, anstehende und gewertete Teams teilen sie sich. */
type StreamTeam = Pick<
    AthleteBoardTeam | AthleteBoardResultTeam,
    | 'startNumber'
    | 'clubsShort'
    | 'clubsFull'
    | 'teamName'
    | 'place'
    | 'timeString'
    | 'penaltySeconds'
    | 'penaltyNote'
    | 'failed'
    | 'failedReason'
    | 'laps'
>

/** Sortierschlüssel mit Nullen am Ende — weder Platz noch Startnummer dürfen NULL nach vorne ziehen. */
const byNullsLast =
    <T,>(key: (team: T) => number | null | undefined) =>
    (a: T, b: T): number => {
        const av = key(a)
        const bv = key(b)
        if (av == null && bv == null) return 0
        if (av == null) return 1
        if (bv == null) return -1
        return av - bv
    }

// Nur deckende Hex-Farben dürfen auf die Key-Fläche: während das Theme lädt, liefert
// MUI rgba-Vorgaben (z. B. text.primary = rgba(0,0,0,0.87)) — die würden sich mit der
// Key-Farbe mischen und beim Keying Farbsäume ziehen. Also im Zweifel ein festes Hex.
const solidOr = (color: string, fallback: string): string =>
    color.startsWith('#') ? color : fallback

/**
 * Das Livestream-Overlay: vollflächige Key-Farbe, unten ein Lower-Third im r2r-Design.
 *
 * Chroma-Regeln: Das Panel ist VOLLSTÄNDIG deckend — keine Halbtransparenz, keine weichen
 * Schatten, kein Blur. Halbtransparente Pixel mischen sich mit der Key-Farbe und erzeugen
 * Farbsäume, sobald der Streamer die Farbe herausfiltert. Harte Kanten, Rundung ist okay.
 */
const BoardStreamOverlayElement = ({view, element}: Props) => {
    const {t} = useTranslation()
    const theme = useTheme()
    const content = streamOverlayContent(view, element.streamMode)
    const keyColor = element.backgroundColor ?? STREAM_DEFAULT_BACKGROUND

    // Leerzustand: reine Key-Farbe — das Overlay verschwindet im Stream von selbst.
    // upcomingList/laps bekommen ihre eigene Darstellung erst in Task 11 — bis dahin
    // fällt die Kachel für diese Modi auf die reine Key-Farbe zurück.
    if (content === null || content.kind === 'upcomingList' || content.kind === 'laps') {
        return <Box sx={{position: 'fixed', inset: 0, backgroundColor: keyColor}} />
    }

    const match = content.kind === 'result' ? null : content.match
    const result = content.kind === 'result' ? content.result : null

    const teams: StreamTeam[] =
        content.kind === 'result'
            ? [...content.result.teams].sort(
                  byNullsLast((team: AthleteBoardResultTeam) => team.place),
              )
            : [...content.match.teams].sort(
                  byNullsLast((team: AthleteBoardTeam) => team.startNumber),
              )

    const clubOf = (team: StreamTeam) =>
        element.useShortNames === false ? team.clubsFull : team.clubsShort

    // Wettkampfname + Runde/Lauf: dieselbe Quelle wie überall (match ODER result), derselbe
    // Dedupe wie ResultsMatchCard — ein Laufname, der nur die Runde wiederholt, entfällt.
    const competitionName = match?.competitionName ?? result?.competitionName
    const roundName = match?.roundName ?? result?.roundName
    const matchName = match?.matchName ?? result?.matchName
    const showMatchName = matchName != null && matchName !== roundName

    const stateLabel =
        content.kind === 'running'
            ? t('event.boards.stream.running')
            : content.kind === 'result'
              ? t('event.boards.stream.result')
              : t('event.boards.stream.upcoming')

    // Startzeit nur beim anstehenden Lauf — beim laufenden/gewerteten Lauf sagt die
    // Kopfzeile ohnehin schon, in welchem Zustand er ist.
    const startTime = content.kind === 'upcoming' ? match?.startTime : null

    return (
        <Box
            sx={{
                position: 'fixed',
                inset: 0,
                backgroundColor: keyColor,
                display: 'flex',
                alignItems: 'flex-end',
            }}>
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
                    {/* Kopfzeile: Zustand + Wettkampf + Runde/Lauf + Startzeit */}
                    <Stack direction="row" alignItems="center" gap={2}>
                        <Box
                            sx={{
                                flexShrink: 0,
                                px: 1.5,
                                py: 0.5,
                                borderRadius: 1,
                                backgroundColor: solidOr(theme.palette.primary.main, '#1976d2'),
                                color: solidOr(theme.palette.primary.contrastText, '#ffffff'),
                            }}>
                            <Typography
                                sx={{
                                    fontWeight: 700,
                                    textTransform: 'uppercase',
                                    letterSpacing: '0.04em',
                                }}>
                                {stateLabel}
                            </Typography>
                        </Box>
                        <Typography
                            variant="h4"
                            noWrap
                            sx={{fontWeight: 700, minWidth: 0, flex: 1}}>
                            {competitionName}
                        </Typography>
                        {startTime && (
                            <Typography variant="h5" sx={{fontWeight: 700, flexShrink: 0}}>
                                {format(new Date(startTime), t('format.time'))}
                            </Typography>
                        )}
                    </Stack>
                    {(roundName || showMatchName) && (
                        <Typography variant="h6" noWrap sx={{fontWeight: 500}}>
                            {[roundName, showMatchName ? matchName : null]
                                .filter(Boolean)
                                .join(' · ')}
                        </Typography>
                    )}

                    {/* Je Boot eine Zeile: Startnummer, Verein/Teamname, Platz/Zeit oder DNF. */}
                    <Stack sx={{gap: 1, overflow: 'hidden'}}>
                        {teams.map(team => {
                            const club = clubOf(team)
                            const trailing = team.failed
                                ? failedLabel(
                                      team.failedReason,
                                      t('event.info.athleteBoard.failed'),
                                  )
                                : [team.place != null ? `${team.place}.` : null, team.timeString]
                                      .filter(Boolean)
                                      .join(' ')
                            return (
                                <Stack
                                    key={team.startNumber}
                                    direction="row"
                                    alignItems="baseline"
                                    gap={2}>
                                    <Typography
                                        variant="h5"
                                        sx={{
                                            fontWeight: 800,
                                            fontVariantNumeric: 'tabular-nums',
                                            minWidth: '1.6em',
                                            flexShrink: 0,
                                        }}>
                                        {team.startNumber}
                                    </Typography>
                                    <Box sx={{minWidth: 0, flex: 1}}>
                                        <Typography variant="h5" noWrap sx={{fontWeight: 700}}>
                                            {[club, team.teamName].filter(Boolean).join(' | ')}
                                        </Typography>
                                        {/* Rundenzeiten: eigene Zeile, tabularNums, kleiner */}
                                        {team.laps && team.laps.length > 0 && (
                                            <Typography
                                                variant="body2"
                                                noWrap
                                                sx={{fontVariantNumeric: 'tabular-nums'}}>
                                                {team.laps
                                                    .map(lap => `${lap.name} ${lap.timeString}`)
                                                    .join(' · ')}
                                            </Typography>
                                        )}
                                        {/* Zeitstrafe: warning-Farbton, Text "…s · {penaltyNote}" */}
                                        {(team.penaltySeconds != null || team.penaltyNote) && (
                                            <Typography
                                                variant="body2"
                                                noWrap
                                                sx={{
                                                    color: solidOr(
                                                        theme.palette.warning.light,
                                                        '#ffb74d',
                                                    ),
                                                }}>
                                                {[
                                                    team.penaltySeconds != null
                                                        ? `${team.penaltySeconds}s`
                                                        : null,
                                                    team.penaltyNote,
                                                ]
                                                    .filter(Boolean)
                                                    .join(' · ')}
                                            </Typography>
                                        )}
                                    </Box>
                                    {trailing && (
                                        <Typography
                                            variant="h5"
                                            sx={{
                                                fontWeight: 700,
                                                fontVariantNumeric: 'tabular-nums',
                                                flexShrink: 0,
                                                textAlign: 'right',
                                            }}>
                                            {trailing}
                                        </Typography>
                                    )}
                                </Stack>
                            )
                        })}
                    </Stack>
                </Stack>
            </Box>
        </Box>
    )
}

export default BoardStreamOverlayElement
