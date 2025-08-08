import {Box, IconButton, Stack, Typography} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import {useAppSession} from '@contexts/app/AppSessionContext.tsx'
import {qrEventRoute} from '@routes'

type Props = {
    title: string
    disableBackButton?: boolean
}

const AppTopTitle = ({title, disableBackButton}: Props) => {
    const {qr} = useAppSession()
    const {eventId} = qrEventRoute.useParams()
    return (
        <Stack direction={'row'} sx={{width: 1, position: 'relative', mb: 1, alignItems: 'center'}}>
            {disableBackButton !== true && (
                <IconButton sx={{position: 'absolute', left: 0}} onClick={() => qr.reset(eventId)}>
                    <ArrowBackIcon />
                </IconButton>
            )}
            <Box sx={{flex: 1, textAlign: 'center'}}>
                <Typography variant="h5">{title}</Typography>
            </Box>
        </Stack>
    )
}

export default AppTopTitle
