// This file is auto-generated by @hey-api/openapi-ts

export type AcceptInvitationRequest = {
    token: string
    password: string
}

export type Action = 'CREATE' | 'READ' | 'UPDATE' | 'DELETE'

export type ApiError = {
    status: {
        value: number
        description: string
    }
    message: string
    errorCode?: ErrorCode
}

export type AppUserDto = {
    id: string
    email: string
    firstname: string
    lastname: string
    roles: Array<RoleDto>
}

export type AppUserInvitationDto = {
    id: string
    email: string
    firstname: string
    lastname: string
    language: EmailLanguage
    expiresAt: string
    createdAt: string
    assignedEmail?: AssignedEmailDto
    roles: Array<RoleDto>
    createdBy?: CreatedByDto
}

export type AppUserRegistrationDto = {
    id: string
    email: string
    firstname: string
    lastname: string
    language: EmailLanguage
    expiresAt: string
    createdAt: string
    assignedEmail?: AssignedEmailDto
}

export type AssignDaysToRaceRequest = {
    days: Array<string>
}

export type AssignedEmailDto = {
    recipient: string
    sentAt?: string
    lastErrorAt?: string
    lastError?: string
}

export type AssignRacesToDayRequest = {
    races: Array<string>
}

export type BadRequestError = ApiError & {
    details?: {
        validExample?: unknown
    }
}

export type CaptchaDto = {
    id: string
    imgSrc: string
    solutionMin: number
    solutionMax: number
    handleToHeightRatio: number
    start: number
}

export type CreatedByDto = {
    firstname: string
    lastname: string
}

export type Duplicate = {
    value: unknown
    count: number
}

export type EmailLanguage = 'DE' | 'EN'

export type ErrorCode = 'CAPTCHA_WRONG' | 'EMAIL_IN_USE'

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

export type Invalid =
    | string
    | {
          field: string
          error: Invalid
      }
    | {
          errorPositions: unknown
      }
    | Duplicate
    | {
          duplicates: Array<Duplicate>
      }
    | {
          value: string
          pattern: string
      }
    | {
          allOf: Array<Invalid>
      }
    | {
          anyOf: Array<Invalid>
      }
    | {
          oneOf: Array<
              | Invalid
              | {
                    [key: string]: unknown
                }
          >
      }

export type InviteRequest = {
    email: string
    firstname: string
    lastname: string
    language: EmailLanguage
    roles: Array<string>
    callbackUrl: string
}

export type LoginDto = {
    id: string
    privileges: Array<PrivilegeDto>
}

export type LoginRequest = {
    email: string
    password: string
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
 * Captcha challenge id
 */
export type Parameterchallenge = string

export type ParametereventDayId = string

export type ParametereventId = string

/**
 * Captcha solution
 */
export type Parameterinput = number

/**
 * Page size for pagination
 */
export type Parameterlimit = number

/**
 * Result offset for pagination
 */
export type Parameteroffset = number

export type ParameterraceId = string

/**
 * Filter result with space-separated search terms for pagination
 */
export type Parametersearch = string

/**
 * Fields with direction (as JSON [{field: <field>, direction: ASC | DESC}, ...]) sorting result for pagination
 */
export type Parametersort = string

export type PasswordResetInitRequest = {
    email: string
    language: EmailLanguage
    callbackUrl: string
}

export type PasswordResetRequest = {
    password: string
}

export type Privilege = {
    action: Action
    resource: Resource
    scope: Scope
}

export type PrivilegeDto = {
    id: string
    action: Action
    resource: Resource
    scope: Scope
}

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
    properties?: RacePropertiesRequestDto
    template?: string
}

export type RaceTemplateDto = {
    id: string
    properties: RacePropertiesDto
}

export type RaceTemplateRequest = {
    properties: RacePropertiesRequestDto
}

export type RegisterRequest = {
    email: string
    password: string
    firstname: string
    lastname: string
    language: EmailLanguage
    callbackUrl: string
}

export type Resource = 'USER' | 'EVENT'

export type RoleDto = {
    id: string
    name: string
    description?: string
    privileges: Array<PrivilegeDto>
}

export type RoleRequest = {
    name: string
    description?: string
    privileges: Array<string>
}

export type Scope = 'OWN' | 'GLOBAL'

export type TooManyRequestsError = ApiError & {
    details: {
        retryAfter: number
    }
}

export type UnprocessableEntityError = ApiError & {
    details:
        | {
              reason: Invalid
          }
        | {
              result: Invalid
          }
}

export type VerifyRegistrationRequest = {
    token: string
}

export type UserLoginData = {
    body: LoginRequest
}

export type UserLoginResponse = LoginDto

export type UserLoginError = BadRequestError | ApiError | TooManyRequestsError

export type CheckUserLoginResponse = LoginDto | void

export type CheckUserLoginError = ApiError

export type UserLogoutResponse = void

export type UserLogoutError = ApiError

export type GetUsersData = {
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

export type GetUsersResponse = {
    data: Array<AppUserDto>
    pagination: Pagination
}

export type GetUsersError = BadRequestError | ApiError | UnprocessableEntityError

export type GetUserData = {
    path: {
        userId: string
    }
}

export type GetUserResponse = AppUserDto

export type GetUserError = BadRequestError | ApiError

export type GetRegistrationsData = {
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

export type GetRegistrationsResponse = {
    data: Array<AppUserRegistrationDto>
    pagination: Pagination
}

export type GetRegistrationsError = BadRequestError | ApiError | UnprocessableEntityError

export type RegisterUserData = {
    body: RegisterRequest
    query: {
        /**
         * Captcha challenge id
         */
        challenge: string
        /**
         * Captcha solution
         */
        input: number
    }
}

export type RegisterUserResponse = void

export type RegisterUserError = BadRequestError | ApiError | UnprocessableEntityError

export type VerifyUserRegistrationData = {
    body: VerifyRegistrationRequest
}

export type VerifyUserRegistrationResponse = unknown

export type VerifyUserRegistrationError = BadRequestError | ApiError | UnprocessableEntityError

export type GetInvitationsData = {
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

export type GetInvitationsResponse = {
    data: Array<AppUserInvitationDto>
    pagination: Pagination
}

export type GetInvitationsError = BadRequestError | ApiError | UnprocessableEntityError

export type InviteUserData = {
    body: InviteRequest
}

export type InviteUserResponse = void

export type InviteUserError = BadRequestError | ApiError | UnprocessableEntityError

export type AcceptUserInvitationData = {
    body: AcceptInvitationRequest
}

export type AcceptUserInvitationResponse = unknown

export type AcceptUserInvitationError = BadRequestError | ApiError | UnprocessableEntityError

export type InitPasswordResetData = {
    body: PasswordResetInitRequest
    query: {
        /**
         * Captcha challenge id
         */
        challenge: string
        /**
         * Captcha solution
         */
        input: number
    }
}

export type InitPasswordResetResponse = void

export type InitPasswordResetError = ApiError | UnprocessableEntityError | TooManyRequestsError

export type ResetPasswordData = {
    body: PasswordResetRequest
    path: {
        passwordResetToken: string
    }
}

export type ResetPasswordResponse = void

export type ResetPasswordError = ApiError | UnprocessableEntityError

export type AddRoleData = {
    body: RoleRequest
}

export type AddRoleResponse = string

export type AddRoleError = BadRequestError | ApiError | UnprocessableEntityError

export type GetRolesData = {
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

export type GetRolesResponse = {
    data: Array<RoleDto>
    pagination: Pagination
}

export type GetRolesError = ApiError

export type UpdateRoleData = {
    body: RoleRequest
    path: {
        roleId: string
    }
}

export type UpdateRoleResponse = void

export type UpdateRoleError = BadRequestError | ApiError | UnprocessableEntityError

export type DeleteRoleData = {
    path: {
        roleId: string
    }
}

export type DeleteRoleResponse = void

export type DeleteRoleError = ApiError

export type AddEventData = {
    body: EventRequest
}

export type AddEventResponse = string

export type AddEventError = BadRequestError | ApiError | UnprocessableEntityError

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

export type GetEventsResponse = {
    data: Array<EventDto>
    pagination: Pagination
}

export type GetEventsError = ApiError

export type GetEventData = {
    path: {
        eventId: string
    }
}

export type GetEventResponse = EventDto

export type GetEventError = BadRequestError | ApiError

export type UpdateEventData = {
    body: EventRequest
    path: {
        eventId: string
    }
}

export type UpdateEventResponse = void

export type UpdateEventError = BadRequestError | ApiError

export type DeleteEventData = {
    path: {
        eventId: string
    }
}

export type DeleteEventResponse = void

export type DeleteEventError = ApiError

export type AddEventDayData = {
    body: EventDayRequest
    path: {
        eventId: string
    }
}

export type AddEventDayResponse = string

export type AddEventDayError = BadRequestError | ApiError

export type GetEventDaysData = {
    path: {
        eventId: string
    }
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

export type GetEventDaysResponse = {
    data: Array<EventDayDto>
    pagination: Pagination
}

export type GetEventDaysError = ApiError

export type GetEventDayData = {
    path: {
        eventDayId: string
        eventId: string
    }
}

export type GetEventDayResponse = EventDayDto

export type GetEventDayError = BadRequestError | ApiError

export type UpdateEventDayData = {
    body: EventDayRequest
    path: {
        eventDayId: string
        eventId: string
    }
}

export type UpdateEventDayResponse = void

export type UpdateEventDayError = BadRequestError | ApiError

export type DeleteEventDayData = {
    path: {
        eventDayId: string
        eventId: string
    }
}

export type DeleteEventDayResponse = void

export type DeleteEventDayError = ApiError

export type AssignRacesToEventDayData = {
    body: AssignRacesToDayRequest
    path: {
        eventDayId: string
        eventId: string
    }
}

export type AssignRacesToEventDayResponse = void

export type AssignRacesToEventDayError = BadRequestError | ApiError

export type AddRaceData = {
    body: RaceRequest
    path: {
        eventId: string
    }
}

export type AddRaceResponse = string

export type AddRaceError = BadRequestError | ApiError

export type GetRacesData = {
    path: {
        eventId: string
    }
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

export type GetRacesResponse = {
    data: Array<RaceDto>
    pagination: Pagination
}

export type GetRacesError = ApiError

export type GetRaceData = {
    path: {
        eventId: string
        raceId: string
    }
}

export type GetRaceResponse = RaceDto

export type GetRaceError = BadRequestError | ApiError

export type UpdateRaceData = {
    body: RaceRequest
    path: {
        eventId: string
        raceId: string
    }
}

export type UpdateRaceResponse = void

export type UpdateRaceError = BadRequestError | ApiError

export type DeleteRaceData = {
    path: {
        eventId: string
        raceId: string
    }
}

export type DeleteRaceResponse = void

export type DeleteRaceError = ApiError

export type AssignDaysToRaceData = {
    body: AssignDaysToRaceRequest
    path: {
        eventId: string
        raceId: string
    }
}

export type AssignDaysToRaceResponse = void

export type AssignDaysToRaceError = BadRequestError | ApiError

export type AddRaceTemplateData = {
    body: RaceTemplateRequest
}

export type AddRaceTemplateResponse = string

export type AddRaceTemplateError = BadRequestError | ApiError

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

export type GetRaceTemplatesResponse = {
    data: Array<RaceTemplateDto>
    pagination: Pagination
}

export type GetRaceTemplatesError = ApiError

export type GetRaceTemplateData = {
    path: {
        raceTemplateId: string
    }
}

export type GetRaceTemplateResponse = RaceTemplateDto

export type GetRaceTemplateError = BadRequestError | ApiError

export type UpdateRaceTemplateData = {
    body: RaceTemplateRequest
    path: {
        raceTemplateId: string
    }
}

export type UpdateRaceTemplateResponse = void

export type UpdateRaceTemplateError = BadRequestError | ApiError

export type DeleteRaceTemplateData = {
    path: {
        raceTemplateId: string
    }
}

export type DeleteRaceTemplateResponse = void

export type DeleteRaceTemplateError = ApiError

export type AddNamedParticipantData = {
    body: NamedParticipantRequest
}

export type AddNamedParticipantResponse = string

export type AddNamedParticipantError = BadRequestError | ApiError

export type GetNamedParticipantsResponse = {
    data: Array<NamedParticipantDto>
    pagination: Pagination
}

export type GetNamedParticipantsError = ApiError

export type UpdateNamedParticipantData = {
    body: NamedParticipantRequest
    path: {
        namedParticipantId: string
    }
}

export type UpdateNamedParticipantResponse = void

export type UpdateNamedParticipantError = BadRequestError | ApiError

export type DeleteNamedParticipantData = {
    path: {
        namedParticipantId: string
    }
}

export type DeleteNamedParticipantResponse = void

export type DeleteNamedParticipantError = ApiError

export type AddRaceCategoryData = {
    body: RaceCategoryRequest
}

export type AddRaceCategoryResponse = string

export type AddRaceCategoryError = BadRequestError | ApiError

export type GetRaceCategoriesResponse = {
    data: Array<RaceCategoryDto>
    pagination: Pagination
}

export type GetRaceCategoriesError = ApiError

export type UpdateRaceCategoryData = {
    body: RaceCategoryRequest
    path: {
        raceCategoryId: string
    }
}

export type UpdateRaceCategoryResponse = void

export type UpdateRaceCategoryError = BadRequestError | ApiError

export type DeleteRaceCategoryData = {
    path: {
        raceCategoryId: string
    }
}

export type DeleteRaceCategoryResponse = void

export type DeleteRaceCategoryError = ApiError

export type NewCaptchaResponse = CaptchaDto

export type NewCaptchaError = ApiError
