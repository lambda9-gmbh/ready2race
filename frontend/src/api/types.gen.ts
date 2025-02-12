// This file is auto-generated by @hey-api/openapi-ts

export type AppUserProperties = {
    email: string
    firstname: string
    lastname: string
    roles: Array<string>
}

export type AppUserResponse = {
    id: string
    properties: AppUserProperties
}

export type AssignDaysToRaceRequest = {
    days: Array<string>
}

export type AssignRacesToDayRequest = {
    races: Array<string>
}

export type EventDayDto = {
    id: string
    event: string
    date: string
    name?: string
    description?: string
}

export type EventDayRequest = {
    date: string
    name?: string
    description?: string
}

export type EventDto = {
    id: string
    name: string
    description?: string
    location?: string
    registrationAvailableFrom?: string
    registrationAvailableTo?: string
    invoicePrefix?: string
}

export type EventRequest = {
    name: string
    description?: string
    location?: string
    registrationAvailableFrom?: string
    registrationAvailableTo?: string
    invoicePrefix?: string
}

export type GetEventDayPageResponse = {
    data: Array<EventDayDto>
    pagination: Pagination
}

export type GetEventPageResponse = {
    data: Array<EventDto>
    pagination: Pagination
}

export type GetRacePageResponse = {
    data: Array<RaceDto>
    pagination: Pagination
}

export type GetRaceTemplatePageResponse = {
    data: Array<RaceTemplateDto>
    pagination: Pagination
}

export type LoginRequest = {
    email: string
    password: string
}

export type LoginResponse = {
    privilegesGlobal: Array<Privilege>
    privilegesBound: Array<Privilege>
}

export type NamedParticipantDto = {
    id: string
    name: string
    description?: string
}

export type NamedParticipantForRaceDto = {
    id: string
    name: string
    description?: string
    required: boolean
    countMales: number
    countFemales: number
    countNonBinary: number
    countMixed: number
}

export type NamedParticipantForRaceRequestDto = {
    namedParticipant: string
    required: boolean
    countMales: number
    countFemales: number
    countNonBinary: number
    countMixed: number
}

export type NamedParticipantRequest = {
    name: string
    description?: string
}

export type Order = {
    field: string
    direction: 'ASC' | 'DESC'
}

export type direction = 'ASC' | 'DESC'

export type Pagination = {
    total: number
    limit: number
    offset: number
    sort: Array<Order>
    search: string
}

/**
 * Page size for pagination
 */
export type Parameterlimit = number

/**
 * Result offset for pagination
 */
export type Parameteroffset = number

/**
 * Filter result with space-separated search terms for pagination
 */
export type Parametersearch = string

/**
 * Fields with direction (as JSON [{field: <field>, direction: ASC | DESC}, ...]) sorting result for pagination
 */
export type Parametersort = string

export type Privilege =
    | 'USER_VIEW'
    | 'USER_EDIT'
    | 'ROLE_VIEW'
    | 'ROLE_EDIT'
    | 'EVENT_EDIT'
    | 'EVENT_VIEW'

export type RaceCategoryDto = {
    id: string
    name: string
    description?: string
}

export type RaceCategoryRequest = {
    name: string
    description?: string
}

export type RaceDto = {
    id: string
    event: string
    properties: RacePropertiesDto
    template?: string
}

export type RacePropertiesDto = {
    identifier: string
    name: string
    shortName?: string
    description?: string
    countMales: number
    countFemales: number
    countNonBinary: number
    countMixed: number
    participationFee: string
    rentalFee: string
    raceCategory?: RaceCategoryDto
    namedParticipants: Array<NamedParticipantForRaceDto>
}

export type RacePropertiesRequestDto = {
    identifier: string
    name: string
    shortName?: string
    description?: string
    countMales: number
    countFemales: number
    countNonBinary: number
    countMixed: number
    participationFee: string
    rentalFee: string
    raceCategory?: string
    namedParticipants: Array<NamedParticipantForRaceRequestDto>
}

export type RaceRequest = {
    properties: RacePropertiesRequestDto
    template?: string
}

export type RaceTemplateDto = {
    id: string
    properties: RacePropertiesDto
}

export type RaceTemplateRequest = {
    properties: RacePropertiesRequestDto
}

export type UserLoginData = {
    body: LoginRequest
}

export type UserLoginResponse = LoginResponse

export type UserLoginError = string

export type CheckUserLoginResponse = LoginResponse | void

export type CheckUserLoginError = string

export type UserLogoutResponse = void

export type UserLogoutError = string

export type AddEventData = {
    body: EventRequest
}

export type AddEventResponse = string

export type AddEventError = string

export type GetEventsData = {
    query?: {
        /**
         * Page size for pagination
         */
        limit?: number
        /**
         * Result offset for pagination
         */
        offset?: number
        /**
         * Filter result with space-separated search terms for pagination
         */
        search?: string
        /**
         * Fields with direction (as JSON [{field: <field>, direction: ASC | DESC}, ...]) sorting result for pagination
         */
        sort?: string
    }
}

export type GetEventsResponse = GetEventPageResponse

export type GetEventsError = string

export type GetEventResponse = EventDto

export type GetEventError = string

export type UpdateEventData = {
    body: EventRequest
}

export type UpdateEventResponse = void

export type UpdateEventError = string

export type DeleteEventResponse = void

export type DeleteEventError = string

export type AddEventDayData = {
    body: EventDayRequest
}

export type AddEventDayResponse = string

export type AddEventDayError = string

export type GetEventDaysData = {
    query?: {
        /**
         * Page size for pagination
         */
        limit?: number
        /**
         * Result offset for pagination
         */
        offset?: number
        /**
         * Optional parameter that filters by raceId
         */
        raceId?: string
        /**
         * Filter result with space-separated search terms for pagination
         */
        search?: string
        /**
         * Fields with direction (as JSON [{field: <field>, direction: ASC | DESC}, ...]) sorting result for pagination
         */
        sort?: string
    }
}

export type GetEventDaysResponse = GetEventDayPageResponse

export type GetEventDaysError = string

export type GetEventDayResponse = EventDayDto

export type GetEventDayError = string

export type UpdateEventDayData = {
    body: EventDayRequest
}

export type UpdateEventDayResponse = void

export type UpdateEventDayError = string

export type DeleteEventDayResponse = void

export type DeleteEventDayError = string

export type AssignRacesToEventDayData = {
    body: AssignRacesToDayRequest
}

export type AssignRacesToEventDayResponse = void

export type AssignRacesToEventDayError = string

export type AddRaceData = {
    body: RaceRequest
}

export type AddRaceResponse = string

export type AddRaceError = string

export type GetRacesData = {
    query?: {
        /**
         * Optional parameter that filters by eventDayId
         */
        eventDayId?: string
        /**
         * Page size for pagination
         */
        limit?: number
        /**
         * Result offset for pagination
         */
        offset?: number
        /**
         * Filter result with space-separated search terms for pagination
         */
        search?: string
        /**
         * Fields with direction (as JSON [{field: <field>, direction: ASC | DESC}, ...]) sorting result for pagination
         */
        sort?: string
    }
}

export type GetRacesResponse = GetRacePageResponse

export type GetRacesError = string

export type GetRaceResponse = RaceDto

export type GetRaceError = string

export type UpdateRaceData = {
    body: RaceRequest
}

export type UpdateRaceResponse = void

export type UpdateRaceError = string

export type DeleteRaceResponse = void

export type DeleteRaceError = string

export type AssignDaysToRaceData = {
    body: AssignDaysToRaceRequest
}

export type AssignDaysToRaceResponse = void

export type AssignDaysToRaceError = string

export type AddRaceTemplateData = {
    body: RaceTemplateRequest
}

export type AddRaceTemplateResponse = string

export type AddRaceTemplateError = string

export type GetRaceTemplatesData = {
    query?: {
        /**
         * Page size for pagination
         */
        limit?: number
        /**
         * Result offset for pagination
         */
        offset?: number
        /**
         * Filter result with space-separated search terms for pagination
         */
        search?: string
        /**
         * Fields with direction (as JSON [{field: <field>, direction: ASC | DESC}, ...]) sorting result for pagination
         */
        sort?: string
    }
}

export type GetRaceTemplatesResponse = GetRaceTemplatePageResponse

export type GetRaceTemplatesError = string

export type GetRaceTemplateResponse = RaceTemplateDto

export type GetRaceTemplateError = string

export type UpdateRaceTemplateData = {
    body: RaceTemplateRequest
}

export type UpdateRaceTemplateResponse = void

export type UpdateRaceTemplateError = string

export type DeleteRaceTemplateResponse = void

export type DeleteRaceTemplateError = string

export type AddNamedParticipantData = {
    body: NamedParticipantRequest
}

export type AddNamedParticipantResponse = string

export type AddNamedParticipantError = string

export type GetNamedParticipantsResponse = Array<NamedParticipantDto>

export type GetNamedParticipantsError = string

export type UpdateNamedParticipantData = {
    body: NamedParticipantRequest
}

export type UpdateNamedParticipantResponse = void

export type UpdateNamedParticipantError = string

export type DeleteNamedParticipantResponse = void

export type DeleteNamedParticipantError = string

export type AddRaceCategoryData = {
    body: RaceCategoryRequest
}

export type AddRaceCategoryResponse = string

export type AddRaceCategoryError = string

export type GetRaceCategoriesResponse = Array<RaceCategoryDto>

export type GetRaceCategoriesError = string

export type UpdateRaceCategoryData = {
    body: RaceCategoryRequest
}

export type UpdateRaceCategoryResponse = void

export type UpdateRaceCategoryError = string

export type DeleteRaceCategoryResponse = void

export type DeleteRaceCategoryError = string
