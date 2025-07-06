import {Button, Stack, Typography} from "@mui/material";
import {CheckQrCodeResponse} from "@api/types.gen.ts";
import {qrEventRoute, router} from "@routes";
import {UseQr} from "@contexts/qr/QrContext.ts";
import {useEffect} from "react";
import QrNimiqScanner from "@components/qrApp/QrNimiqScanner.tsx";
import {useTranslation} from "react-i18next";

const QrScannerPage = () => {
    const { t } = useTranslation();
    const navigate = router.navigate
    const {eventId} = qrEventRoute.useParams()
    const qr = UseQr()

    function handleScannerResult(qrCodeId: string, response: CheckQrCodeResponse | null) {
        qr.update({...qr, qrCodeId: qrCodeId, response: response, received: true})
    }

    useEffect(() => {
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
    }, [qr])

    return (
        <Stack spacing={2} p={2} alignItems="center" justifyContent="center">
            <Typography variant="h2" textAlign="center">
                {t('qrScanner.title')}
            </Typography>
            <QrNimiqScanner callback={handleScannerResult}></QrNimiqScanner>
            <Button onClick={() => navigate({to: "/app"})}>{t('common.back')}</Button>
        </Stack>
    )
};

export default QrScannerPage;