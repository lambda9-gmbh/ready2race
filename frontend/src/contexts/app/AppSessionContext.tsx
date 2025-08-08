import React, {createContext, PropsWithChildren, useContext, useEffect, useState} from 'react'
import {CheckQrCodeResponse, EventDto} from '@api/types.gen.ts'
import {router} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getEvents} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import {useUser} from '@contexts/user/UserContext.ts'
import {getUserAppRights} from "@components/qrApp/common.ts";

export type AppFunction =
    | 'APP_QR_MANAGEMENT'
    | 'APP_COMPETITION_CHECK'
    | 'APP_EVENT_REQUIREMENT'
    | 'APP_CATERER'
    | null

export type QrState = {
    qrCodeId: string | null
    response: CheckQrCodeResponse | null
    received: boolean
    update: (state: Partial<QrState>) => void
    reset: (eventId: string) => void
}

interface AppSessionContextType {
    appFunction: AppFunction
    setAppFunction: (fn: AppFunction) => void
    availableAppFunctions: AppFunction[]
    setAvailableAppFunctions: (fns: AppFunction[]) => void
    qr: QrState
    goBack: () => void
    showBackButton: boolean
    events: EventDto[]
}

const AppSessionContext = createContext<AppSessionContextType | undefined>(undefined)

export const AppSessionProvider: React.FC<PropsWithChildren> = ({children}) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()

    // Persistiere appFunction im sessionStorage
    const [appFunction, setAppFunctionState] = useState<AppFunction>(() => {
        return (sessionStorage.getItem('appFunction') as AppFunction) || null
    })

    const [availableAppFunctions, setAvailableAppFunctions] = useState<AppFunction[]>(() => {
        const persisted = (sessionStorage.getItem('appFunction') as AppFunction) || null
        return persisted ? [persisted] : []
    })

    const [showBackButton, setShowBackButton] = useState(true)

    const navigate = router.navigate

    const {data: eventsData} = useFetch(signal => getEvents({signal}), {
        onResponse: response => {
            if (response.error) {
                feedback.error(t('common.load.error.multiple.short', {entity: t('event.event')}))
            }
        },
        deps: [],
    })

    const [goingBack, setGoingBack] = useState<boolean>(false)
    useEffect(() => {
        if (appFunction === null) {
            setGoingBack(true)
        } else{
            if(eventsData && eventsData.data.length === 1 ){
                navigate({to: '/app/$eventId/scanner', params: {eventId: eventsData.data[0].id}})
            }
        }
    }, [appFunction])

    useEffect(() => {
        if(user){
            const rights = getUserAppRights(user)
            setAvailableAppFunctions(rights)
        }
    }, [user]);

    useEffect(() => {
        if (availableAppFunctions.length === 1 && (eventsData?.data.length ?? 0) < 2) {
            setShowBackButton(false)
        } else {
            setShowBackButton(true)
        }
        if (goingBack) {
            if (appFunction === null) {
                navigate({to: '/app/function'})
            } else {
                navigate({to: '/app'})
            }
            setGoingBack(false)
        }
    }, [availableAppFunctions, goingBack])

    const goBack = () => {
        console.log("Go back")
        if ((eventsData?.data.length ?? 0) > 1) {
            console.log("var1")
            navigate({to: '/app'})
        } else {
            console.log("var2")
            setAppFunction(null)
        }
    }

    const setAppFunction = (fn: AppFunction) => {
        setAppFunctionState(fn)
        if (fn) {
            sessionStorage.setItem('appFunction', fn)
        } else {
            sessionStorage.removeItem('appFunction')
        }
    }

    const [qrState, setQrState] = useState<Omit<QrState, 'update' | 'reset'>>({
        qrCodeId: null,
        response: null,
        received: false,
    })

    const update = (state: Partial<QrState>) => {
        setQrState(prev => ({...prev, ...state}))
    }

    const reset = (eventId: string) => {
        setQrState({qrCodeId: null, response: null, received: false})
        navigate({to: '/app/$eventId/scanner', params: {eventId: eventId}})
    }

    const qr: QrState = {...qrState, update, reset}

    return eventsData ? (
        <AppSessionContext.Provider
            value={{
                appFunction,
                setAppFunction,
                availableAppFunctions,
                setAvailableAppFunctions,
                qr,
                goBack,
                showBackButton,
                events: eventsData?.data,
            }}>
            {children}
        </AppSessionContext.Provider>
    ) : (
        <></>
    )
}

export const useAppSession = (): AppSessionContextType => {
    const ctx = useContext(AppSessionContext)
    if (!ctx) throw new Error('AppSessionContext not found')
    return ctx
}
