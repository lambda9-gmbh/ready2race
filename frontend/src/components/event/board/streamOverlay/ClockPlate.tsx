import {Box, useTheme} from '@mui/material'
import {AthleteBoardMatch} from '@api/types.gen.ts'
import StreamClockLabel from './StreamClockLabel.tsx'
import {solidOr} from './streamDisplay.ts'
import useStreamClockDisplay from './useStreamClockDisplay.ts'

interface ClockPlateProps {
    match: AthleteBoardMatch
    clockOffsetMs: number
}

/**
 * Modus „Nur Laufuhr": eine kompakte, separat croppbare Uhr-Platte unten links —
 * dieselbe Stelle wie das Lower-Third, damit beide Quellen im Zweifel deckungsgleich
 * liegen. Die Platte erscheint erst, wenn die Uhr etwas zu zeigen hat (deckende Fläche
 * schiebt per Transform herein, der Uhrtext übernimmt den erlaubten Text-Fade), und
 * verschwindet mit dem Fade-out der Uhr wieder vollständig.
 */
const ClockPlate = ({match, clockOffsetMs}: ClockPlateProps) => {
    const theme = useTheme()
    const clock = useStreamClockDisplay(match, clockOffsetMs)

    if (!clock.mounted) {
        return null
    }

    return (
        <Box sx={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'flex-end'}}>
            <Box
                sx={{
                    m: 3,
                    display: 'flex',
                    borderRadius: 2,
                    overflow: 'hidden',
                    backgroundColor: solidOr(theme.palette.text.primary, '#1d1d1d'),
                    color: solidOr(theme.palette.background.paper, '#ffffff'),
                    '@keyframes r2rStreamClockIn': {
                        from: {transform: 'translateY(24px)'},
                        to: {transform: 'translateY(0)'},
                    },
                    animation: 'r2rStreamClockIn 350ms ease-out',
                }}>
                <Box
                    sx={{
                        width: '0.6rem',
                        flexShrink: 0,
                        backgroundColor: solidOr(theme.palette.primary.main, '#1976d2'),
                    }}
                />
                <Box sx={{px: 3, py: 1.5}}>
                    <StreamClockLabel match={match} clockOffsetMs={clockOffsetMs} variant="h2" />
                </Box>
            </Box>
        </Box>
    )
}

export default ClockPlate
