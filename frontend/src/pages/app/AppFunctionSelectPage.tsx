import {Button, Stack, Typography} from '@mui/material';
import {useUser} from '@contexts/user/UserContext';
import {AppFunction, useAppSession} from '@contexts/app/AppSessionContext.tsx';
import {useEffect, useState} from 'react';
import {router} from "@routes";
import { updateAppQrManagementGlobal, updateAppCompetitionCheckGlobal, updateAppEventRequirementGlobal } from '@authorization/privileges';
import { useTranslation } from 'react-i18next';

const APP_FUNCTIONS = [
    {fn: 'APP_QR_MANAGEMENT' as AppFunction, labelKey: 'app.functionSelect.functions.qrManagement' as const},
    {fn: 'APP_COMPETITION_CHECK' as AppFunction, labelKey: 'app.functionSelect.functions.competitionCheck' as const},
    {fn: 'APP_EVENT_REQUIREMENT' as AppFunction, labelKey: 'app.functionSelect.functions.eventRequirement' as const},
] as const;

const AppFunctionSelectPage = () => {
    const { t } = useTranslation();
    const user = useUser();
    const {setAppFunction} = useAppSession();
    const navigate = router.navigate
    const [available, setAvailable] = useState<AppFunction[]>([]);

    useEffect(() => {
        // Prüfe, welche App-Funktionen der User hat
        const rights: AppFunction[] = [];
        if (user.checkPrivilege(updateAppQrManagementGlobal)) rights.push('APP_QR_MANAGEMENT');
        if (user.checkPrivilege(updateAppCompetitionCheckGlobal)) rights.push('APP_COMPETITION_CHECK');
        if (user.checkPrivilege(updateAppEventRequirementGlobal)) rights.push('APP_EVENT_REQUIREMENT');
        setAvailable(rights);
        // Wenn der User nur ein Recht hat, direkt weiterleiten
        if (rights.length === 1) {
            setAppFunction(rights[0]);
            navigate({to: '/app'});
        }
        // Wenn kein Recht, ggf. Forbidden
        if (rights.length === 0) {
            navigate({to: '/app/forbidden'});
        }
    }, [user, setAppFunction, navigate]);

    const handleSelect = (fn: AppFunction) => {
        setAppFunction(fn);
        navigate({to: '/app'});
    };

    if (available.length <= 1) return null;

    return (
        <Stack spacing={2} alignItems="center" justifyContent="center" p={4}>
            <Typography variant="h4" textAlign="center">
                {t('app.functionSelect.title')}
            </Typography>
            {APP_FUNCTIONS.filter(f => available.includes(f.fn)).map(f => (
                <Button key={f.fn} variant="contained" fullWidth onClick={() => handleSelect(f.fn)}>
                    {t(f.labelKey)}
                </Button>
            ))}
        </Stack>
    );
};

export default AppFunctionSelectPage; 