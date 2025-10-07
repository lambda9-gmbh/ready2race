import React, {createContext, PropsWithChildren, useCallback, useContext, useEffect, useMemo, useState} from 'react'
import {CheckQrCodeResponse, EventDto} from '@api/types.gen.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {useNavigate} from "@tanstack/react-router";
import {useFeedback, useFetch} from "@utils/hooks.ts";
import {getEvents} from "@api/sdk.gen.ts";
import {useTranslation} from "react-i18next";

export type AppFunction =
    | 'APP_QR_MANAGEMENT'
    | 'APP_COMPETITION_CHECK'
    | 'APP_EVENT_REQUIREMENT'
    | 'APP_CATERER'
    | null

export type AppView =
    | 'APP_Event_List'
    | 'APP_Function_Select'
    | 'APP_Scanner'
    | 'APP_Participant'
    | 'App_Assign'
    | 'App_User'
    | 'App_Login'
    | 'APP_Forbidden'

const appViewPaths: Record<AppView, string> = {
    'APP_Event_List': '/app',
    'APP_Function_Select': '/app/function',
    'APP_Scanner': '/app/scanner',
    'APP_Participant': '/app/participant',
    'App_Assign': '/app/assign',
    'App_User': '/app/user',
    'App_Login': '/app/login',
    'APP_Forbidden': '/app/forbidden',
}

export type AppViewState = {
    view: AppView,
    replace?: boolean,
}

export type QrState = {
    qrCodeId: string | null
    response: CheckQrCodeResponse | null
    received: boolean
    handled: boolean
    update: (state: Omit<QrState, 'update' | 'reset'>) => void
    reset: () => void
}

interface AppSessionContextType {
    appFunction: AppFunction
    setAppFunction: (fn: AppFunction) => void
    setEventId: (fn: string) => void
    eventId: string
    qr: QrState
    events: EventDto[] | undefined,
    setEvents: (events: EventDto[]) => void
    navigateTo: (view: AppView, replace?: boolean) => void
}

const AppSessionContext = createContext<AppSessionContextType | undefined>(undefined)

export const AppSessionProvider: React.FC<PropsWithChildren> = ({children}) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()
    const [viewState, setViewState] = useState<AppViewState>()
    // Persistiere appFunction im sessionStorage
    const [appFunction, setAppFunctionState] = useState<AppFunction>(() => {
        return (sessionStorage.getItem('appFunction') as AppFunction) || null
    })

    const [events, setEvents] = useState<EventDto[]>()

    // Persistiere eventId im sessionStorage
    const [eventId, setEventIdValue] = useState<string>(() => {
        return (sessionStorage.getItem('eventId')) || ""
    })

    useFetch(signal => getEvents({signal}), {
        onResponse: response => {
            if (response.data) {
                setEvents(response.data.data)
            }
            if (response.error) {
                feedback.error(t('common.load.error.multiple.short', {entity: t('event.event')}))
            }
        },
        preCondition: () => user.loggedIn,
        deps: [user.loggedIn],
    })

    const navigate = useNavigate()

    useEffect(() => {

        if (viewState !== undefined) {
            void navigate({to: appViewPaths[viewState.view], replace: viewState.replace})
        }

    }, [viewState, navigate]);

    const setAppFunction = (fn: AppFunction) => {
        setAppFunctionState(fn)
        if (fn) {
            sessionStorage.setItem('appFunction', fn)
        } else {
            sessionStorage.removeItem('appFunction')
        }
    }

    const setEventId = (fn: string) => {
        setEventIdValue(fn)
        if (fn.length > 0) {
            sessionStorage.setItem('eventId', fn)
        } else {
            sessionStorage.removeItem('eventId')
        }
    }

    const [qrState, setQrState] = useState<Omit<QrState, 'update' | 'reset'>>({
        qrCodeId: null,
        response: null,
        received: false,
        handled: false,
    })

    const qr: QrState = useMemo(() => ({
        ...qrState,
        update: (state: Omit<QrState, 'update' | 'reset'>) => {
            setQrState(prev => ({...prev, ...state}))
        },
        reset: () => {
            setQrState({qrCodeId: null, response: null, received: false, handled: false})
        }
    }), [qrState])

    const navigateTo = useCallback((view: AppView, replace: boolean = false) => {
        setViewState({view, replace})
    }, [])

    return (
        <AppSessionContext.Provider
            value={{
                appFunction,
                setAppFunction,
                setEventId,
                eventId,
                qr,
                events,
                setEvents: (events) => setEvents(events),
                navigateTo,
            }}>
            {children}
        </AppSessionContext.Provider>
    )
}

export const useAppSession = (): AppSessionContextType => {
    const ctx = useContext(AppSessionContext)
    if (!ctx) throw new Error('AppSessionContext not found')
    return ctx
}
