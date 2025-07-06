import {CheckQrCodeResponse} from "@api/types.gen.ts";
import {createContext, useContext} from "react";

export type QrDataReceived = {
    qrCodeId: string,
    response: CheckQrCodeResponse | null,
    update: (data: QrDataReceived) => void,
    reset: (eventId: string) => void,
    received: true
}

export type QrDataPending = {
    qrCodeId: string,
    response: CheckQrCodeResponse | null,
    update: (data: QrDataReceived) => void,
    reset: (eventId: string) => void,
    received: false
}

export type QrData = QrDataPending | QrDataReceived

export const QrContext = createContext<QrData | null>(null)

export const UseQr = (): QrData => {
    const qr = useContext(QrContext)
    if (qr === null) {
        throw Error('Qr context not initialized')
    }
    return qr
}

export const UseReceivedQr = (): QrData => {
    const qr = UseQr()
    if (!qr.received) {
        return qr
    }
    return qr
}