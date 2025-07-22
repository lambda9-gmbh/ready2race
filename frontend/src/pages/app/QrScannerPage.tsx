import {Button, Stack, Typography, useMediaQuery, useTheme} from "@mui/material";
import {CheckQrCodeResponse} from "@api/types.gen.ts";
import {qrEventRoute, router} from "@routes";
import {useEffect} from "react";
import QrNimiqScanner from "@components/qrApp/QrNimiqScanner.tsx";
import {useTranslation} from "react-i18next";
import {useAppSession} from '@contexts/app/AppSessionContext';
import {checkQrCode} from '@api/sdk.gen.ts';
import {useFeedback} from "@utils/hooks.ts";

const uuidRegex = /([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/;

const QrScannerPage = () => {
    const { t } = useTranslation();
    const navigate = router.navigate
    const {eventId} = qrEventRoute.useParams()
    const { qr, appFunction } = useAppSession();
    const feedback = useFeedback();
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

    useEffect(() => {
        if (!appFunction) {
            navigate({to: '/app/function'});
            return;
        }
        if (qr.received) {
            const response = qr.response

            if (response === null) {
                navigate({to: "/app/$eventId/assign", params: {eventId: eventId}})
            } else if (response.type == "Participant") {
                navigate({to: "/app/$eventId/participant", params: {eventId: eventId}})
            } else if (response.type == "User") {
                navigate({to: "/app/$eventId/user", params: {eventId: eventId}})
            } else {
                console.log("Diese Meldung wollen wir nicht sehen")
            }
        }
    }, [qr, appFunction, navigate])

    async function handleScannerResult(qrCodeContent: string) {
        const match = qrCodeContent.match(uuidRegex);
        if (match) {
            const qrCodeId = match[1];
            try {
                const result = await checkQrCode({ path: { qrCodeId } });
                let response: CheckQrCodeResponse | null = null;
                if (result.data && Object.keys(result.data).length > 0) {
                    response = result.data;
                }
                qr.update({...qr, qrCodeId: qrCodeId, response: response, received: true});
            } catch (error) {
                feedback.error(
                    t('common.load.error.single', {
                        entity: t('task.task'),
                    })
                );
                console.error(error);
            }
        }
    }

    return (
        <Stack 
            spacing={3} 
            alignItems="center" 
            justifyContent="center"
            sx={{ 
                minHeight: '60vh',
                px: { xs: 2, sm: 3 },
                py: 2
            }}
        >
            <Typography 
                variant={isMobile ? "h4" : "h3"} 
                textAlign="center"
            >
                {t('qrScanner.title')}
            </Typography>
            <QrNimiqScanner callback={handleScannerResult} />
            <Stack 
                spacing={2} 
                sx={{ 
                    width: '100%', 
                    maxWidth: 400,
                    mt: 2 
                }}
            >
                <Button
                    onClick={() => handleScannerResult('b294a2e0-039d-4ede-bd84-c61a47dd9c04')}
                    fullWidth
                    size="large"
                    variant="contained"
                    color="primary"
                    sx={{
                        py: { xs: 1.5, sm: 2 }
                    }}
                >
                    Dev (Test)
                </Button>
                <Button 
                    onClick={() => navigate({to: "/app"})} 
                    fullWidth
                    size="large"
                    variant="outlined"
                >
                    {t('common.back')}
                </Button>
            </Stack>
        </Stack>
    )
};

export default QrScannerPage;