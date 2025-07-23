import { Stack, Typography, Button } from '@mui/material';
import { router } from '@routes';
import { useTranslation } from 'react-i18next';
import {useUser} from "@contexts/user/UserContext.ts";

const ForbiddenPage = () => {
    const { t } = useTranslation();
    const navigate = router.navigate;
    const user = useUser();

    return (
        <Stack spacing={2} alignItems="center" justifyContent="center" p={4}>
            <Typography variant="h2" color="error" textAlign="center">
                {t('app.forbidden.title')}
            </Typography>
            <Typography textAlign="center">
                {t('app.forbidden.message')}
            </Typography>
            <Button variant="contained" onClick={() => {
                if(user.loggedIn) {
                    user.logout()
                }
                navigate({to: '/app'})
            }}>
                {t('app.forbidden.backButton')}
            </Button>
        </Stack>
    );
};

export default ForbiddenPage; 