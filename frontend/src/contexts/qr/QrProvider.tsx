import {QrContext, QrData, QrDataPending, QrDataReceived} from "@contexts/qr/QrContext.ts";
import {PropsWithChildren, useState} from "react";
import {router} from "@routes";

const QrProvider = ({children}: PropsWithChildren) => {
    const [qrData, setQrData] = useState<QrDataReceived>()
    const navigate = router.navigate

    const setValues = (data: QrDataReceived) => {
        setQrData({...data})
    }

    const reset = (eventId: string) => {
        setQrData(undefined)
        navigate({to: "/app/$eventId/scanner", params: {eventId: eventId}})
    }

    let value: QrData
    if (!qrData) {
        value = {
            update: (data) => {
                setValues(data)
            },
            reset: (eventId: string) => {
                reset(eventId)
            },
            qrCodeId: "",
            response: null,
            received: false
        } satisfies QrDataPending
    } else {
        value = {
            ...qrData,
            received: true,
        } satisfies QrDataReceived
    }

    return <QrContext.Provider value={value}>{children}</QrContext.Provider>
}

export default QrProvider