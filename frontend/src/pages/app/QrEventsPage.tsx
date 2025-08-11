import {Box, Button, Stack, Typography} from '@mui/material'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import {router} from '@routes'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext'
import {getUserAppRights} from "@components/qrApp/common.ts";
import {useUser} from "@contexts/user/UserContext.ts";
import {useEffect} from "react";

const QrEventsPage = () => {
    const {t} = useTranslation()
    const navigate = router.navigate
    const {setAppFunction, events} = useAppSession()
    const user = useUser()

    const availableAppFunctions = getUserAppRights(user)

    useEffect(() => {
        if (events.length === 1) {
            navigate({to: '/app/$eventId/scanner', params: {eventId: events[0].id}})
        }
    }, [events, navigate])

    return (
        <Box sx={{width: 1, maxWidth: 600}}>
            <Stack spacing={2} sx={{width: 1}}>
                <Typography variant="h4" textAlign="center" gutterBottom>
                    {t('qrEvents.title')}
                </Typography>
                {events?.map(event => (
                    <Button
                        key={event.id}
                        onClick={() =>
                            navigate({
                                to: '/app/$eventId/scanner',
                                params: {eventId: event.id},
                            })
                        }
                        fullWidth
                        variant="contained"
                        color="primary">
                        {event.name}
                    </Button>
                ))}
            </Stack>
            {(availableAppFunctions.length ?? 0) > 1 && (
                <Button
                    onClick={() => {
                        setAppFunction(null)
                    }}
                    variant="outlined"
                    startIcon={<SwapHorizIcon />}
                    fullWidth
                    sx={{mt: 4}}>
                    {t('qrEvents.switchApp')}
                </Button>
            )}
        </Box>
    )
}

export default QrEventsPage
