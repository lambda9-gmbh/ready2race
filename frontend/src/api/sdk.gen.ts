// This file is auto-generated by @hey-api/openapi-ts

import {createClient, createConfig, type OptionsLegacyParser} from '@hey-api/client-fetch'
import type {
    UserLoginData,
    UserLoginError,
    UserLoginResponse,
    CheckUserLoginError,
    CheckUserLoginResponse,
    UserLogoutError,
    UserLogoutResponse,
    AddEventData,
    AddEventError,
    AddEventResponse,
    GetEventsData,
    GetEventsError,
    GetEventsResponse,
    GetEventError,
    GetEventResponse2,
    UpdateEventError,
    UpdateEventResponse,
    DeleteEventError,
    DeleteEventResponse,
} from './types.gen'

export const client = createClient(createConfig())

export const userLogin = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<UserLoginData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<UserLoginResponse, UserLoginError, ThrowOnError>({
        ...options,
        url: '/login',
    })
}

export const checkUserLogin = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).get<
        CheckUserLoginResponse,
        CheckUserLoginError,
        ThrowOnError
    >({
        ...options,
        url: '/login',
    })
}

export const userLogout = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).delete<UserLogoutResponse, UserLogoutError, ThrowOnError>({
        ...options,
        url: '/login',
    })
}

export const addEvent = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<AddEventData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<AddEventResponse, AddEventError, ThrowOnError>({
        ...options,
        url: '/event',
    })
}

export const getEvents = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<GetEventsData, ThrowOnError>,
) => {
    return (options?.client ?? client).get<GetEventsResponse, GetEventsError, ThrowOnError>({
        ...options,
        url: '/event',
    })
}

export const getEvent = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).get<GetEventResponse2, GetEventError, ThrowOnError>({
        ...options,
        url: '/event/{eventId}',
    })
}

export const updateEvent = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).put<UpdateEventResponse, UpdateEventError, ThrowOnError>({
        ...options,
        url: '/event/{eventId}',
    })
}

export const deleteEvent = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).delete<DeleteEventResponse, DeleteEventError, ThrowOnError>({
        ...options,
        url: '/event/{eventId}',
    })
}
