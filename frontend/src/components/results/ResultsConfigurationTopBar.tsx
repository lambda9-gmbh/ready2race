import {Box, IconButton} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import {Link} from '@tanstack/react-router'
import LanguageWidget from '@components/appbar/LanguageWidget.tsx'

type Props = {
    showBackButton: boolean
    competitionSelected?: boolean
    resetSelectedCompetition?: () => void
}

const ResultsConfigurationTopBar = (props: Props) => {
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
            <LanguageWidget />
        </Box>
    )
}

export default ResultsConfigurationTopBar
