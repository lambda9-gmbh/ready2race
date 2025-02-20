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
    GetUsersData,
    GetUsersError,
    GetUsersResponse,
    GetUserError,
    GetUserResponse,
    GetRegistrationsData,
    GetRegistrationsError,
    GetRegistrationsResponse,
    RegisterUserData,
    RegisterUserError,
    RegisterUserResponse,
    VerifyUserRegistrationData,
    VerifyUserRegistrationError,
    VerifyUserRegistrationResponse,
    GetInvitationsData,
    GetInvitationsError,
    GetInvitationsResponse,
    InviteUserData,
    InviteUserError,
    InviteUserResponse,
    AcceptUserInvitationData,
    AcceptUserInvitationError,
    AcceptUserInvitationResponse,
    InitPasswordResetData,
    InitPasswordResetError,
    InitPasswordResetResponse,
    ResetPasswordData,
    ResetPasswordError,
    ResetPasswordResponse,
    AddRoleData,
    AddRoleError,
    AddRoleResponse,
    GetRolesData,
    GetRolesError,
    GetRolesResponse,
    UpdateRoleData,
    UpdateRoleError,
    UpdateRoleResponse,
    DeleteRoleData,
    DeleteRoleError,
    DeleteRoleResponse,
    AddEventData,
    AddEventError,
    AddEventResponse,
    GetEventsData,
    GetEventsError,
    GetEventsResponse,
    GetEventError,
    GetEventResponse,
    UpdateEventData,
    UpdateEventError,
    UpdateEventResponse,
    DeleteEventError,
    DeleteEventResponse,
    AddEventDayData,
    AddEventDayError,
    AddEventDayResponse,
    GetEventDaysData,
    GetEventDaysError,
    GetEventDaysResponse,
    GetEventDayError,
    GetEventDayResponse,
    UpdateEventDayData,
    UpdateEventDayError,
    UpdateEventDayResponse,
    DeleteEventDayError,
    DeleteEventDayResponse,
    AssignRacesToEventDayData,
    AssignRacesToEventDayError,
    AssignRacesToEventDayResponse,
    AddRaceData,
    AddRaceError,
    AddRaceResponse,
    GetRacesData,
    GetRacesError,
    GetRacesResponse,
    GetRaceError,
    GetRaceResponse,
    UpdateRaceData,
    UpdateRaceError,
    UpdateRaceResponse,
    DeleteRaceError,
    DeleteRaceResponse,
    AssignDaysToRaceData,
    AssignDaysToRaceError,
    AssignDaysToRaceResponse,
    AddRaceTemplateData,
    AddRaceTemplateError,
    AddRaceTemplateResponse,
    GetRaceTemplatesData,
    GetRaceTemplatesError,
    GetRaceTemplatesResponse,
    GetRaceTemplateError,
    GetRaceTemplateResponse,
    UpdateRaceTemplateData,
    UpdateRaceTemplateError,
    UpdateRaceTemplateResponse,
    DeleteRaceTemplateError,
    DeleteRaceTemplateResponse,
    AddNamedParticipantData,
    AddNamedParticipantError,
    AddNamedParticipantResponse,
    GetNamedParticipantsError,
    GetNamedParticipantsResponse,
    UpdateNamedParticipantData,
    UpdateNamedParticipantError,
    UpdateNamedParticipantResponse,
    DeleteNamedParticipantError,
    DeleteNamedParticipantResponse,
    AddRaceCategoryData,
    AddRaceCategoryError,
    AddRaceCategoryResponse,
    GetRaceCategoriesError,
    GetRaceCategoriesResponse,
    UpdateRaceCategoryData,
    UpdateRaceCategoryError,
    UpdateRaceCategoryResponse,
    DeleteRaceCategoryError,
    DeleteRaceCategoryResponse,
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

export const getUsers = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<GetUsersData, ThrowOnError>,
) => {
    return (options?.client ?? client).get<GetUsersResponse, GetUsersError, ThrowOnError>({
        ...options,
        url: '/user',
    })
}

export const getUser = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).get<GetUserResponse, GetUserError, ThrowOnError>({
        ...options,
        url: '/user/{userId}',
    })
}

export const getRegistrations = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<GetRegistrationsData, ThrowOnError>,
) => {
    return (options?.client ?? client).get<
        GetRegistrationsResponse,
        GetRegistrationsError,
        ThrowOnError
    >({
        ...options,
        url: '/user/registration',
    })
}

export const registerUser = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<RegisterUserData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<RegisterUserResponse, RegisterUserError, ThrowOnError>({
        ...options,
        url: '/user/registration',
    })
}

export const verifyUserRegistration = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<VerifyUserRegistrationData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<
        VerifyUserRegistrationResponse,
        VerifyUserRegistrationError,
        ThrowOnError
    >({
        ...options,
        url: '/user/registration/verify',
    })
}

export const getInvitations = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<GetInvitationsData, ThrowOnError>,
) => {
    return (options?.client ?? client).get<
        GetInvitationsResponse,
        GetInvitationsError,
        ThrowOnError
    >({
        ...options,
        url: '/user/invitation',
    })
}

export const inviteUser = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<InviteUserData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<InviteUserResponse, InviteUserError, ThrowOnError>({
        ...options,
        url: '/user/invitation',
    })
}

export const acceptUserInvitation = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<AcceptUserInvitationData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<
        AcceptUserInvitationResponse,
        AcceptUserInvitationError,
        ThrowOnError
    >({
        ...options,
        url: '/user/invitation/accept',
    })
}

export const initPasswordReset = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<InitPasswordResetData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<
        InitPasswordResetResponse,
        InitPasswordResetError,
        ThrowOnError
    >({
        ...options,
        url: '/user/resetPassword',
    })
}

export const resetPassword = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<ResetPasswordData, ThrowOnError>,
) => {
    return (options?.client ?? client).put<ResetPasswordResponse, ResetPasswordError, ThrowOnError>(
        {
            ...options,
            url: '/user/resetPassword/{passwordResetToken}',
        },
    )
}

export const addRole = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<AddRoleData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<AddRoleResponse, AddRoleError, ThrowOnError>({
        ...options,
        url: '/role',
    })
}

export const getRoles = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<GetRolesData, ThrowOnError>,
) => {
    return (options?.client ?? client).get<GetRolesResponse, GetRolesError, ThrowOnError>({
        ...options,
        url: '/role',
    })
}

export const updateRole = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<UpdateRoleData, ThrowOnError>,
) => {
    return (options?.client ?? client).put<UpdateRoleResponse, UpdateRoleError, ThrowOnError>({
        ...options,
        url: '/role/{roleId}',
    })
}

export const deleteRole = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<DeleteRoleData, ThrowOnError>,
) => {
    return (options?.client ?? client).delete<DeleteRoleResponse, DeleteRoleError, ThrowOnError>({
        ...options,
        url: '/role/{roleId}',
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
    options?: OptionsLegacyParser<GetEventsData, ThrowOnError>,
) => {
    return (options?.client ?? client).get<GetEventsResponse, GetEventsError, ThrowOnError>({
        ...options,
        url: '/event',
    })
}

export const getEvent = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).get<GetEventResponse, GetEventError, ThrowOnError>({
        ...options,
        url: '/event/{eventId}',
    })
}

export const updateEvent = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<UpdateEventData, ThrowOnError>,
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

export const addEventDay = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<AddEventDayData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<AddEventDayResponse, AddEventDayError, ThrowOnError>({
        ...options,
        url: '/event/{eventId}/eventDay',
    })
}

export const getEventDays = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<GetEventDaysData, ThrowOnError>,
) => {
    return (options?.client ?? client).get<GetEventDaysResponse, GetEventDaysError, ThrowOnError>({
        ...options,
        url: '/event/{eventId}/eventDay',
    })
}

export const getEventDay = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).get<GetEventDayResponse, GetEventDayError, ThrowOnError>({
        ...options,
        url: '/event/{eventId}/eventDay/{eventDayId}',
    })
}

export const updateEventDay = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<UpdateEventDayData, ThrowOnError>,
) => {
    return (options?.client ?? client).put<
        UpdateEventDayResponse,
        UpdateEventDayError,
        ThrowOnError
    >({
        ...options,
        url: '/event/{eventId}/eventDay/{eventDayId}',
    })
}

export const deleteEventDay = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).delete<
        DeleteEventDayResponse,
        DeleteEventDayError,
        ThrowOnError
    >({
        ...options,
        url: '/event/{eventId}/eventDay/{eventDayId}',
    })
}

export const assignRacesToEventDay = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<AssignRacesToEventDayData, ThrowOnError>,
) => {
    return (options?.client ?? client).put<
        AssignRacesToEventDayResponse,
        AssignRacesToEventDayError,
        ThrowOnError
    >({
        ...options,
        url: '/event/{eventId}/eventDay/{eventDayId}/races',
    })
}

export const addRace = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<AddRaceData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<AddRaceResponse, AddRaceError, ThrowOnError>({
        ...options,
        url: '/event/{eventId}/race',
    })
}

export const getRaces = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<GetRacesData, ThrowOnError>,
) => {
    return (options?.client ?? client).get<GetRacesResponse, GetRacesError, ThrowOnError>({
        ...options,
        url: '/event/{eventId}/race',
    })
}

export const getRace = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).get<GetRaceResponse, GetRaceError, ThrowOnError>({
        ...options,
        url: '/event/{eventId}/race/{raceId}',
    })
}

export const updateRace = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<UpdateRaceData, ThrowOnError>,
) => {
    return (options?.client ?? client).put<UpdateRaceResponse, UpdateRaceError, ThrowOnError>({
        ...options,
        url: '/event/{eventId}/race/{raceId}',
    })
}

export const deleteRace = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).delete<DeleteRaceResponse, DeleteRaceError, ThrowOnError>({
        ...options,
        url: '/event/{eventId}/race/{raceId}',
    })
}

export const assignDaysToRace = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<AssignDaysToRaceData, ThrowOnError>,
) => {
    return (options?.client ?? client).put<
        AssignDaysToRaceResponse,
        AssignDaysToRaceError,
        ThrowOnError
    >({
        ...options,
        url: '/event/{eventId}/race/{raceId}/days',
    })
}

export const addRaceTemplate = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<AddRaceTemplateData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<
        AddRaceTemplateResponse,
        AddRaceTemplateError,
        ThrowOnError
    >({
        ...options,
        url: '/raceTemplate',
    })
}

export const getRaceTemplates = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<GetRaceTemplatesData, ThrowOnError>,
) => {
    return (options?.client ?? client).get<
        GetRaceTemplatesResponse,
        GetRaceTemplatesError,
        ThrowOnError
    >({
        ...options,
        url: '/raceTemplate',
    })
}

export const getRaceTemplate = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).get<
        GetRaceTemplateResponse,
        GetRaceTemplateError,
        ThrowOnError
    >({
        ...options,
        url: '/raceTemplate/{raceTemplateId}',
    })
}

export const updateRaceTemplate = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<UpdateRaceTemplateData, ThrowOnError>,
) => {
    return (options?.client ?? client).put<
        UpdateRaceTemplateResponse,
        UpdateRaceTemplateError,
        ThrowOnError
    >({
        ...options,
        url: '/raceTemplate/{raceTemplateId}',
    })
}

export const deleteRaceTemplate = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).delete<
        DeleteRaceTemplateResponse,
        DeleteRaceTemplateError,
        ThrowOnError
    >({
        ...options,
        url: '/raceTemplate/{raceTemplateId}',
    })
}

export const addNamedParticipant = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<AddNamedParticipantData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<
        AddNamedParticipantResponse,
        AddNamedParticipantError,
        ThrowOnError
    >({
        ...options,
        url: '/namedParticipant',
    })
}

export const getNamedParticipants = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).get<
        GetNamedParticipantsResponse,
        GetNamedParticipantsError,
        ThrowOnError
    >({
        ...options,
        url: '/namedParticipant',
    })
}

export const updateNamedParticipant = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<UpdateNamedParticipantData, ThrowOnError>,
) => {
    return (options?.client ?? client).put<
        UpdateNamedParticipantResponse,
        UpdateNamedParticipantError,
        ThrowOnError
    >({
        ...options,
        url: '/namedParticipant/{namedParticipantId}',
    })
}

export const deleteNamedParticipant = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).delete<
        DeleteNamedParticipantResponse,
        DeleteNamedParticipantError,
        ThrowOnError
    >({
        ...options,
        url: '/namedParticipant/{namedParticipantId}',
    })
}

export const addRaceCategory = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<AddRaceCategoryData, ThrowOnError>,
) => {
    return (options?.client ?? client).post<
        AddRaceCategoryResponse,
        AddRaceCategoryError,
        ThrowOnError
    >({
        ...options,
        url: '/raceCategory',
    })
}

export const getRaceCategories = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).get<
        GetRaceCategoriesResponse,
        GetRaceCategoriesError,
        ThrowOnError
    >({
        ...options,
        url: '/raceCategory',
    })
}

export const updateRaceCategory = <ThrowOnError extends boolean = false>(
    options: OptionsLegacyParser<UpdateRaceCategoryData, ThrowOnError>,
) => {
    return (options?.client ?? client).put<
        UpdateRaceCategoryResponse,
        UpdateRaceCategoryError,
        ThrowOnError
    >({
        ...options,
        url: '/raceCategory/{raceCategoryId}',
    })
}

export const deleteRaceCategory = <ThrowOnError extends boolean = false>(
    options?: OptionsLegacyParser<unknown, ThrowOnError>,
) => {
    return (options?.client ?? client).delete<
        DeleteRaceCategoryResponse,
        DeleteRaceCategoryError,
        ThrowOnError
    >({
        ...options,
        url: '/raceCategory/{raceCategoryId}',
    })
}
