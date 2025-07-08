import {Button, Stack} from "@mui/material";
import QrScanner from "@components/qrApp/QrScanner.tsx";
import {CheckQrCodeResponse} from "@api/types.gen.ts";
import {qrEventRoute, router} from "@routes";
import {UseQr} from "@contexts/qr/QrContext.ts";
import {useEffect} from "react";

const QrScannerPage = () => {
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
            } else if (Array.isArray(response)) {
                navigate({to: "/app/$eventId/participant", params: {eventId: eventId}})
            } else if (response.type == "User") {
                navigate({to: "/app/$eventId/user", params: {eventId: eventId}})
            } else {
                console.log("Diese Meldung wollen wir nicht sehen")
            }
        }
    }, [qr])

    return (
        <Stack>
            <QrScanner callback={handleScannerResult}></QrScanner>
            <Button onClick={() => navigate({to: "/app"})}>Zurück</Button>
        </Stack>
    )
};

export default QrScannerPage;