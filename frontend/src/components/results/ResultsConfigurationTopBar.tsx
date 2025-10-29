import {Box, IconButton, Typography, useMediaQuery, useTheme} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import {Link} from '@tanstack/react-router'
import LanguageWidget from '@components/appbar/LanguageWidget.tsx'

type Props = {
    showBackButton: boolean
    competitionSelected?: boolean
    resetSelectedCompetition?: () => void
    title?: string
}

const ResultsConfigurationTopBar = (props: Props) => {
    const theme = useTheme()
    const smallScreenLayout = useMediaQuery(`(max-width:${theme.breakpoints.values.sm}px)`)

    return (
        <Box
            sx={{
                width: '100%',
                display: 'flex',
                justifyContent: props.showBackButton ? 'space-between' : 'end',
            }}>
            {props.showBackButton && (
                <Box>
                    {props.competitionSelected ? (
                        <IconButton onClick={props.resetSelectedCompetition}>
                            <ArrowBackIcon />
                        </IconButton>
                    ) : (
                        <Link to={'/results'}>
                            <IconButton>
                                <ArrowBackIcon />
                            </IconButton>
                        </Link>
                    )}
                </Box>
            )}
            {props.title && !smallScreenLayout && (
                <Typography variant={'h6'}>{props.title}</Typography>
            )}
            <LanguageWidget />
        </Box>
    )
}

export default ResultsConfigurationTopBar
