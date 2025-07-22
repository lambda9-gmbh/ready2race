import {Button, Divider, Stack, Typography} from "@mui/material";
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import {useFetch} from "@utils/hooks.ts";
import {getEvents} from "@api/sdk.gen.ts";
import {router} from "@routes";
import {useTranslation} from "react-i18next";
import {AppFunction, useAppSession} from '@contexts/app/AppSessionContext';
import {useUser} from '@contexts/user/UserContext';
import {useEffect} from 'react';
import { updateAppQrManagementGlobal, updateAppCompetitionCheckGlobal, updateAppEventRequirementGlobal } from '@authorization/privileges';

const QrEventsPage = () => {
    const { t } = useTranslation();
    const navigate = router.navigate
    const {data} = useFetch(signal => getEvents({signal}))
    const { appFunction, setAppFunction } = useAppSession();
    const user = useUser();

    useEffect(() => {
        if (!appFunction) {
            navigate({to: '/app/function'});
            return;
        }
        // Prüfe, welche App-Funktionen der User hat
        const rights: AppFunction[] = [];
        if (user.checkPrivilege(updateAppQrManagementGlobal)) rights.push('APP_QR_MANAGEMENT');
        if (user.checkPrivilege(updateAppCompetitionCheckGlobal)) rights.push('APP_COMPETITION_CHECK');
        if (user.checkPrivilege(updateAppEventRequirementGlobal)) rights.push('APP_EVENT_REQUIREMENT');
        if (rights.length === 0) {
            navigate({to: '/app/forbidden'});
        } else if (rights.length > 1 && !appFunction) {
            navigate({to: '/app/function'});
        } else if (rights.length === 1 && !appFunction) {
            setAppFunction(rights[0]);
        }
    }, [user, appFunction, setAppFunction, navigate]);

    const switchAppLabel = (t('qrEvents.switchApp'));

    return (
        <Stack 
            spacing={2} 
            alignItems="center" 
            justifyContent="center"
            sx={{ width: '100%', maxWidth: 600 }}
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
            <Button onClick={() => navigate({to: '/app/function'})} variant="outlined" startIcon={<SwapHorizIcon />} fullWidth>
                {switchAppLabel}
            </Button>
        </Stack>
    )
}

export default QrEventsPage