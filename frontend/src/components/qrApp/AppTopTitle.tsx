import {Box, IconButton, Stack, Typography} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import {useAppSession} from '@contexts/app/AppSessionContext.tsx'

type Props = {
    title: string
    disableBackButton?: boolean
}
const AppTopTitle = ({title, disableBackButton}: Props) => {

    const {navigateTo} = useAppSession()
    return (
        <Stack direction={'row'} sx={{width: 1, position: 'relative', mb: 1, alignItems: 'center'}}>
            {disableBackButton !== true && (
                <IconButton sx={{position: 'absolute', left: 0}} onClick={() => {
                    navigateTo("APP_Scanner")
                }}>
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
