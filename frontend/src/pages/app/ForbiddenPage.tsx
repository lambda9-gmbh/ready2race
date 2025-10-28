import { Stack, Typography, Button } from '@mui/material';
import { useTranslation } from 'react-i18next';
import {useAppSession} from "@contexts/app/AppSessionContext.tsx";

const ForbiddenPage = () => {
    const { t } = useTranslation();
    const {navigateTo} = useAppSession()

    return (
        <Stack spacing={2} alignItems="center" justifyContent="center" p={4}>
            <Typography variant="h2" color="error" textAlign="center">
                {t('app.forbidden.title')}
            </Typography>
            <Typography textAlign="center">
                {t('app.forbidden.message')}
            </Typography>
            <Button variant="contained" onClick={() => {
                navigateTo("App_Login")
            }}>
                {t('app.forbidden.backButton')}
            </Button>
        </Stack>
    );
};

export default ForbiddenPage; 