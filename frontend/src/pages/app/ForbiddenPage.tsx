import { Stack, Typography, Button } from '@mui/material';
import { router } from '@routes';

const ForbiddenPage = () => {
    const navigate = router.navigate;
    return (
        <Stack spacing={2} alignItems="center" justifyContent="center" p={4}>
            <Typography variant="h2" color="error" textAlign="center">
                Nicht berechtigt
            </Typography>
            <Typography textAlign="center">
                Du bist für diese Seite nicht berechtigt.
            </Typography>
            <Button variant="contained" onClick={() => navigate({to: '/app'})}>
                Zurück zur App
            </Button>
        </Stack>
    );
};

export default ForbiddenPage; 