import {Box, Card, CardActionArea, CardContent, Stack, Typography} from '@mui/material';
import {useUser} from '@contexts/user/UserContext';
import {AppFunction, useAppSession} from '@contexts/app/AppSessionContext.tsx';
import {useEffect, useState} from 'react';
import {router} from "@routes";
import { updateAppQrManagementGlobal, updateAppCompetitionCheckGlobal, updateAppEventRequirementGlobal, updateAppCatererGlobal } from '@authorization/privileges';
import { useTranslation } from 'react-i18next';
import QrCodeIcon from '@mui/icons-material/QrCode';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import AssignmentIcon from '@mui/icons-material/Assignment';
import RestaurantIcon from '@mui/icons-material/Restaurant';

const APP_FUNCTIONS = [
    {
        fn: 'APP_QR_MANAGEMENT' as AppFunction, 
        labelKey: 'app.functionSelect.functions.qrManagement' as const,
        icon: QrCodeIcon
    },
    {
        fn: 'APP_COMPETITION_CHECK' as AppFunction, 
        labelKey: 'app.functionSelect.functions.competitionCheck' as const,
        icon: CheckCircleIcon
    },
    {
        fn: 'APP_EVENT_REQUIREMENT' as AppFunction, 
        labelKey: 'app.functionSelect.functions.eventRequirement' as const,
        icon: AssignmentIcon
    },
    {
        fn: 'APP_CATERER' as AppFunction,
        labelKey: 'app.functionSelect.functions.caterer' as const,
        icon: RestaurantIcon
    },
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
        if (user.checkPrivilege(updateAppCatererGlobal)) rights.push('APP_CATERER');
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
        <Stack spacing={4} alignItems="center" justifyContent="center" p={4}>
            <Typography variant="h4" textAlign="center">
                {t('app.functionSelect.title')}
            </Typography>
            <Box 
                display="grid" 
                gridTemplateColumns="repeat(auto-fit, minmax(250px, 1fr))"
                gap={3}
                width="100%"
                maxWidth="800px"
            >
                {APP_FUNCTIONS.filter(f => available.includes(f.fn)).map(f => {
                    const Icon = f.icon;
                    return (
                        <Card 
                            key={f.fn} 
                            sx={{ 
                                height: '200px',
                                display: 'flex',
                                flexDirection: 'column',
                                transition: 'transform 0.2s',
                                '&:hover': {
                                    transform: 'scale(1.05)',
                                    boxShadow: 3
                                }
                            }}
                        >
                            <CardActionArea 
                                onClick={() => handleSelect(f.fn)}
                                sx={{ 
                                    height: '100%',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    justifyContent: 'center',
                                    alignItems: 'center',
                                    p: 3
                                }}
                                className="cursor-pointer"
                            >
                                <CardContent sx={{ textAlign: 'center' }}>
                                    <Icon sx={{ fontSize: 60, mb: 2, color: 'primary.main' }} />
                                    <Typography variant="h6">
                                        {t(f.labelKey)}
                                    </Typography>
                                </CardContent>
                            </CardActionArea>
                        </Card>
                    );
                })}
            </Box>
        </Stack>
    );
};

export default AppFunctionSelectPage; 