import {Box, Typography, useTheme} from '@mui/material'
import {solidOr} from './streamDisplay.ts'

interface StreamStateBadgeProps {
    label: string
    /** Nur „LÄUFT" bekommt einen Punkt vor dem Label — „on air", nicht nur ein Wort. */
    indicator?: boolean
}

/** Zustands-Akzent im Kopf jedes Panels: „LÄUFT" / „ERGEBNIS" / „ALS NÄCHSTES", Primärfarbe. */
const StreamStateBadge = ({label, indicator = false}: StreamStateBadgeProps) => {
    const theme = useTheme()
    return (
        <Box
            sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
                flexShrink: 0,
                px: 1.5,
                py: 0.5,
                borderRadius: 1,
                backgroundColor: solidOr(theme.palette.primary.main, '#1976d2'),
                color: solidOr(theme.palette.primary.contrastText, '#ffffff'),
            }}>
            {indicator && (
                <Box
                    sx={{
                        width: '0.55rem',
                        height: '0.55rem',
                        borderRadius: '50%',
                        backgroundColor: 'currentColor',
                    }}
                />
            )}
            <Typography sx={{fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.04em'}}>
                {label}
            </Typography>
        </Box>
    )
}

export default StreamStateBadge
