import React, {createContext, PropsWithChildren, useContext, useEffect, useState} from 'react'
import {CheckQrCodeResponse, EventDto} from '@api/types.gen.ts'
import {router} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getEvents} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import {useUser} from '@contexts/user/UserContext.ts'
import {getUserAppRights} from '@components/qrApp/common.ts'

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

    const availableAppFunctions = getUserAppRights(user)

    const navigate = router.navigate

    const {data: eventsData} = useFetch(signal => getEvents({signal}), {
        onResponse: response => {
            if (response.error) {
                feedback.error(t('common.load.error.multiple.short', {entity: t('event.event')}))
            }
        },
        deps: [],
    })

    const showBackButton = !(availableAppFunctions.length === 1 && (eventsData?.data.length ?? 0) < 2)

    useEffect(() => {
        // if (appFunction === null) {
        //     navigate({to: '/app/function'})
        // } else {
        //     if (eventsData && eventsData.data.length === 1) {
        //         navigate({to: '/app/$eventId/scanner', params: {eventId: eventsData.data[0].id}})
        //     } else {
        //         navigate({to: '/app'})
        //     }
        // }
        if (eventsData) {
            if (eventsData.data.length === 1 && appFunction === null) {
                navigate({to: '/app/$eventId/function', params: {eventId: eventsData.data[0].id}})
            } else {
                if (appFunction) {
                    navigate({to: '/app/$eventId/scanner', params: {eventId: eventsData.data[0].id}})
                } else {
                    navigate({to: '/app/$eventId/function', params: {eventId: eventsData.data[0].id}})
                }

            }
        } else {
            navigate({to: '/app'})
        }
        // if (eventsData && eventsData.data.length === 1) {
        //     navigate({to: '/app/$eventId/function', params: {eventId: eventsData.data[0].id}})
        // } else {
        //     if (eventsData && appFunction !== null) {
        //         navigate({to: '/app/$eventId/scanner', params: {eventId: eventsData.data[0].id}})
        //     } else {
        //         navigate({to: '/app'})
        //     }
        // }
    }, [appFunction])

    const setAppFunction = (fn: AppFunction) => {
        setAppFunctionState(fn)
        if (fn) {
            sessionStorage.setItem('appFunction', fn)
        } else {
            sessionStorage.removeItem('appFunction')
        }
    }

    const goBack = () => {
        // if ((eventsData?.data.length ?? 0) > 1) {
        //     navigate({to: '/app/$eventId/function', params: {eventId: eventsData.data[0].id}})
        // } else {

        if (appFunction && ((availableAppFunctions.length ?? 0) > 1)) {
            setAppFunction(null)
        } else {
            navigate({to: '/app'})
        }
        // }
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
