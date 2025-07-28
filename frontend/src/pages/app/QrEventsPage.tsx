import {Button, Divider, Stack, Typography} from "@mui/material";
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import {useFetch} from "@utils/hooks.ts";
import {getEvents} from "@api/sdk.gen.ts";
import {router} from "@routes";
import {useTranslation} from "react-i18next";
import {useAppSession} from '@contexts/app/AppSessionContext';
import {useUser} from '@contexts/user/UserContext';
import {useEffect} from 'react';

const QrEventsPage = () => {
    const {t} = useTranslation();
    const navigate = router.navigate
    const {data} = useFetch(signal => getEvents({signal}))
    const { appFunction, setAppFunction } = useAppSession();
    const user = useUser();

    useEffect(() => {
        if (!appFunction) {
            navigate({to: '/app/function'});
            return;
        }
    }, [user, appFunction, setAppFunction, navigate]);

    return (
        <Stack
            spacing={2}
            alignItems="center"
            justifyContent="center"
            sx={{width: '100%', maxWidth: 600}}
        >
            <Typography variant="h4" textAlign="center" gutterBottom>
                {t('qrEvents.title')}
            </Typography>
            {data?.data?.map(event =>
                <Button
                    key={event.id}
                    onClick={() => navigate({
                        to: "/app/$eventId/scanner",
                        params: {eventId: event.id}
                    })}
                    fullWidth
                    variant="contained"
                    color="primary"
                >
                    {event.name}
                </Button>
            )}
            <Divider variant={"fullWidth"} orientation={"horizontal"}></Divider>
            <Button onClick={() => {
                setAppFunction(null);
                navigate({to: '/app/function'});
            }} variant="outlined" startIcon={<SwapHorizIcon/>}
                    fullWidth>
                {t('qrEvents.switchApp')}
            </Button>
        </Stack>
    )
}

export default QrEventsPage