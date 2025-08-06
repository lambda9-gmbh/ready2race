import {Box, Button, Stack, Typography} from '@mui/material'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import {router} from '@routes'
import {useTranslation} from 'react-i18next'
import {AppFunction, useAppSession} from '@contexts/app/AppSessionContext'
import {useUser} from '@contexts/user/UserContext'
import {useEffect, useState} from 'react'
import {getAppRights} from '@components/qrApp/common.ts'

const QrEventsPage = () => {
    const {t} = useTranslation()
    const navigate = router.navigate
    const {appFunction, setAppFunction, events} = useAppSession()
    const user = useUser()

    const [availableRights, setAvailableRights] = useState<AppFunction[]>([])


    useEffect(() => {
        const rights = getAppRights(user)
        setAvailableRights(rights)
    }, [user, appFunction, setAppFunction, navigate])

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
            {availableRights.length > 1 && (
                <Button
                    onClick={() => {
                        setAppFunction(null)
                        navigate({
                            to: '/app/function',
                        })
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
