import {Box, Button, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext'
import {getUserAppRights} from "@components/qrApp/common.ts";
import {useFeedback, useFetch} from "@utils/hooks.ts";
import {getEvents} from "@api/sdk.gen.ts";
import LogoutIcon from '@mui/icons-material/Logout';
import {useUser} from "@contexts/user/UserContext.ts";
import {useEffect} from "react";

const QrEventsPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {setEvents, setEventId, navigateTo, setAppFunction} = useAppSession()
    const user = useUser()
    const availableAppFunctions = getUserAppRights(user)

    const {data} = useFetch(signal => getEvents({signal}), {
        onResponse: response => {
            if (response.data) {
                setEvents(response.data.data)
            }
            if (response.error) {
                feedback.error(t('common.load.error.multiple.short', {entity: t('event.event')}))
            }
        },
        deps: [],
    })

    useEffect(() => {
        if (data && data.data.length === 1) {
            setEventId(data.data[0].id)
            goForward(true)
        }
    }, [data]);

    function goForward(replace: boolean = false) {
        if (availableAppFunctions.length === 1) {
            setAppFunction(availableAppFunctions[0])
            navigateTo("APP_Scanner", replace)
        } else {
            navigateTo("APP_Function_Select", replace)
        }
    }
    return (
        <Box sx={{width: 1, maxWidth: 600}}>
            <Stack spacing={2} sx={{width: 1}}>
                <Typography variant="h4" textAlign="center" gutterBottom>
                    {t('qrEvents.title')}
                </Typography>
                {data?.data?.map(event => (
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
