import React, {createContext, PropsWithChildren, useContext, useState} from 'react';
import {CheckQrCodeResponse} from "@api/types.gen.ts";
import {router} from "@routes";

export type AppFunction = 'APP_QR_MANAGEMENT' | 'APP_COMPETITION_CHECK' | 'APP_EVENT_REQUIREMENT' | null;

export type QrState = {
    qrCodeId: string | null;
    response: CheckQrCodeResponse | null;
    received: boolean;
    update: (state: Partial<QrState>) => void;
    reset: (eventId: string) => void;
};

interface AppSessionContextType {
    appFunction: AppFunction;
    setAppFunction: (fn: AppFunction) => void;
    qr: QrState;
}

const AppSessionContext = createContext<AppSessionContextType | undefined>(undefined);

export const AppSessionProvider: React.FC<PropsWithChildren> = ({children}) => {
    // Persistiere appFunction im sessionStorage
    const [appFunction, setAppFunctionState] = useState<AppFunction>(() => {
        return (sessionStorage.getItem('appFunction') as AppFunction) || null;
    });

    const navigate = router.navigate

    const setAppFunction = (fn: AppFunction) => {
        setAppFunctionState(fn);
        if (fn) {
            sessionStorage.setItem('appFunction', fn);
        } else {
            sessionStorage.removeItem('appFunction');
        }
    };

    const [qrState, setQrState] = useState<Omit<QrState, 'update' | 'reset'>>({
        qrCodeId: null,
        response: null,
        received: false,
    });

    const update = (state: Partial<QrState>) => {
        setQrState(prev => ({...prev, ...state}));
    };

    const reset = (eventId: string) => {
        setQrState({qrCodeId: null, response: null, received: false});
        navigate({to: "/app/$eventId/scanner", params: {eventId: eventId}})
    };

    const qr: QrState = {...qrState, update, reset};

    return (
        <AppSessionContext.Provider value={{appFunction, setAppFunction, qr}}>
            {children}
        </AppSessionContext.Provider>
    );
};

export const useAppSession = (): AppSessionContextType => {
    const ctx = useContext(AppSessionContext);
    if (!ctx) throw new Error('AppSessionContext not found');
    return ctx;
}; 