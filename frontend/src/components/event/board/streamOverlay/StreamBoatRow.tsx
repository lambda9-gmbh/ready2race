import {Box, Stack, Typography, useTheme} from '@mui/material'
import {crewLines, solidOr, StreamCrewMode, StreamTeam, teamTrailingLabel} from './streamDisplay.ts'

interface StreamBoatRowProps {
    team: StreamTeam
    crewMode: StreamCrewMode
    useShortNames: boolean
    failedFallback: string
    /** 'compact' im Lower-Third, 'large' in den zentrierten Panels — dieselbe Zeile,
     *  nur mit der größeren TV-Grafik-Typografie statt der schmaleren Lower-Third-Schrift. */
    size: 'compact' | 'large'
}

/**
 * Eine Bootszeile: Startnummer, Besatzung (nach `streamCrew`), Rundenzeiten, Zeitstrafe,
 * Platz/Zeit oder DNF/DNS/DSQ. Geteilt vom Lower-Third, Ergebnis- und Als-Nächstes-Panel —
 * dieselbe Zeile, nur unterschiedlich groß.
 */
const StreamBoatRow = ({team, crewMode, useShortNames, failedFallback, size}: StreamBoatRowProps) => {
    const theme = useTheme()
    const large = size === 'large'
    const {primary, secondary} = crewLines(team, crewMode, useShortNames)
    const trailing = teamTrailingLabel(team, failedFallback)

    return (
        <Stack direction="row" alignItems="baseline" gap={2} sx={{width: 1}}>
            <Typography
                variant={large ? 'h4' : 'h5'}
                sx={{
                    fontWeight: 800,
                    fontVariantNumeric: 'tabular-nums',
                    minWidth: large ? '2em' : '1.6em',
                    flexShrink: 0,
                }}>
                {team.startNumber}
            </Typography>
            <Box sx={{minWidth: 0, flex: 1}}>
                <Typography variant={large ? 'h4' : 'h5'} noWrap sx={{fontWeight: 700}}>
                    {primary}
                </Typography>
                {/* Zweite Zeile nach `streamCrew`: Besatzung ODER Verein, je nachdem was
                    NICHT schon prominent oben steht (CLUBS_ONLY hat gar keine). */}
                {secondary && (
                    <Typography variant={large ? 'body1' : 'body2'} noWrap>
                        {secondary}
                    </Typography>
                )}
                {/* Rundenzeiten: eigene Zeile, tabularNums, kleiner. */}
                {team.laps && team.laps.length > 0 && (
                    <Typography variant="body2" noWrap sx={{fontVariantNumeric: 'tabular-nums'}}>
                        {team.laps.map(lap => `${lap.name} ${lap.timeString}`).join(' · ')}
                    </Typography>
                )}
                {/* Zeitstrafe: Warnfarbe, Text "…s · {penaltyNote}". */}
                {(team.penaltySeconds != null || team.penaltyNote) && (
                    <Typography
                        variant="body2"
                        noWrap
                        sx={{color: solidOr(theme.palette.warning.light, '#ffb74d')}}>
                        {[team.penaltySeconds != null ? `${team.penaltySeconds}s` : null, team.penaltyNote]
                            .filter(Boolean)
                            .join(' · ')}
                    </Typography>
                )}
            </Box>
            {trailing && (
                <Typography
                    variant={large ? 'h4' : 'h5'}
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
}

export default StreamBoatRow
