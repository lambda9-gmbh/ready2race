import {Box, Button, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext'
import {getUserAppRights} from "@components/qrApp/common.ts";
import LogoutIcon from '@mui/icons-material/Logout';
import {useUser} from "@contexts/user/UserContext.ts";
import {useEffect} from "react";

const QrEventsPage = () => {
    const {t} = useTranslation()
    const {events, setEventId, navigateTo, setAppFunction} = useAppSession()
    const user = useUser()
    const availableAppFunctions = getUserAppRights(user)

    useEffect(() => {
        if (events && events.length === 1) {
            setEventId(events[0].id)
            goForward(true)
        }
    }, [events])

    function goForward(replace: boolean = false) {
        if (availableAppFunctions.length === 1) {
            setAppFunction(availableAppFunctions[0])
            navigateTo("APP_Scanner", replace)
        } else {
            navigateTo("APP_Function_Select", replace)
        }
    }
    return (events &&
        <Box sx={{width: 1, maxWidth: 600}}>
            <Stack spacing={2} sx={{width: 1}}>
                <Typography variant="h4" textAlign="center" gutterBottom>
                    {t('qrEvents.title')}
                </Typography>
                {events.map(event => (
                    <Button
                        key={event.id}
                        onClick={() => {
                            setEventId(event.id)
                            goForward()
                        }}
                        fullWidth
                        variant="contained"
                        color="primary">
                        {event.name}
                    </Button>
                ))}
                <Button
                    onClick={ () => 'logout' in user && user.logout(true)}
                    variant="outlined"
                    startIcon={<LogoutIcon/>}
                    fullWidth
                    sx={{mt: 4}}>
                    {t('user.settings.logout')}
                </Button>
            </Stack>
        </Box>
    )
}

export default QrEventsPage
