import {Box, IconButton, Typography, useMediaQuery, useTheme} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import {Link} from '@tanstack/react-router'
import LanguageWidget from '@components/appbar/LanguageWidget.tsx'

type Props = {
    competitionSelected?: boolean
    resetSelectedCompetition?: () => void
    title?: string
    navigateToHome?: boolean
}

const ResultsConfigurationTopBar = (props: Props) => {
    const theme = useTheme()
    const smallScreenLayout = useMediaQuery(`(max-width:${theme.breakpoints.values.sm}px)`)

    return (
        <Box
            sx={{
                width: '100%',
                display: 'flex',
                justifyContent: 'space-between',
                mb: smallScreenLayout ? 0 : 1,
            }}>
            <Box>
                {props.navigateToHome ? (
                    <Link to={'/'}>
                        <IconButton>
                            <ArrowBackIcon />
                        </IconButton>
                    </Link>
                ) : props.competitionSelected ? (
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
            {props.title && !smallScreenLayout && (
                <Typography variant={'h6'}>{props.title}</Typography>
            )}
            <LanguageWidget />
        </Box>
    )
}

export default ResultsConfigurationTopBar
