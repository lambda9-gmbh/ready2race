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
    createdBy?: AppUserNameDto
}

export type AppUserNameDto = {
    firstname: string
    lastname: string
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

export type AssignCompetitionsToDayRequest = {
    competitions: Array<string>
}

export type AssignDaysToCompetitionRequest = {
    days: Array<string>
}

export type AssignedEmailDto = {
    recipient: string
    sentAt?: string
    lastErrorAt?: string
    lastError?: string
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

export type CompetitionCategoryDto = {
    id: string
    name: string
    description?: string
}

export type CompetitionCategoryRequest = {
    name: string
    description?: string
}

export type CompetitionDto = {
    id: string
    event: string
    properties: CompetitionPropertiesDto
    template?: string
}

export type CompetitionPropertiesDto = {
    identifier: string
    name: string
    shortName?: string
    description?: string
    competitionCategory?: CompetitionCategoryDto
    namedParticipants: Array<NamedParticipantForCompetitionDto>
    fees: Array<FeeForCompetitionDto>
}

export type CompetitionPropertiesRequestDto = {
    identifier: string
    name: string
    shortName?: string
    description?: string
    competitionCategory?: string
    namedParticipants: Array<NamedParticipantForCompetitionRequestDto>
    fees: Array<FeeForCompetitionRequestDto>
}

export type CompetitionRequest = {
    properties?: CompetitionPropertiesRequestDto
    template?: string
}

export type CompetitionTemplateDto = {
    id: string
    properties: CompetitionPropertiesDto
}

export type CompetitionTemplateRequest = {
    properties: CompetitionPropertiesRequestDto
}

export type Duplicate = {
    value: unknown
    count: number
}

export type EmailLanguage = 'DE' | 'EN'

export type ErrorCode = 'CAPTCHA_WRONG' | 'EMAIL_IN_USE' | 'CANNOT_ASSIGN_ROLES'

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

export type EventDocumentDto = {
    id: string
    documentType?: EventDocumentTypeDto
    name: string
    createdAt: string
    createdBy?: AppUserNameDto
    updatedAt: string
    updatedBy?: AppUserNameDto
}

export type EventDocumentRequest = {
    documentType?: string
}

export type EventDocumentTypeDto = {
    id: string
    name: string
    required: boolean
    confirmationRequired: boolean
}

export type EventDocumentTypeRequest = {
    name: string
    required: boolean
    confirmationRequired: boolean
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

export type FeeDto = {
    id: string
    name: string
    description?: string
}

export type FeeForCompetitionDto = {
    id: string
    name: string
    description?: string
    required: boolean
    amount: string
}

export type FeeForCompetitionRequestDto = {
    fee: string
    required: boolean
    amount: string
}

export type FeeRequest = {
    name: string
    description?: string
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

export type NamedParticipantForCompetitionDto = {
    id: string
    name: string
    description?: string
    countMales: number
    countFemales: number
    countNonBinary: number
    countMixed: number
}

export type NamedParticipantForCompetitionRequestDto = {
    namedParticipant: string
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

export type ParametercompetitionId = string

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

export type UserLoginError =
    | BadRequestError
    | ApiError
    | UnprocessableEntityError
    | TooManyRequestsError

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

export type VerifyUserRegistrationData = {
    body: VerifyRegistrationRequest
}

export type VerifyUserRegistrationResponse = unknown

export type VerifyUserRegistrationError = BadRequestError | ApiError | UnprocessableEntityError

export type InviteUserData = {
    body: InviteRequest
}

export type InviteUserResponse = void

export type InviteUserError = BadRequestError | ApiError | UnprocessableEntityError

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

export type InitPasswordResetError =
    | BadRequestError
    | ApiError
    | UnprocessableEntityError
    | TooManyRequestsError

export type ResetPasswordData = {
    body: PasswordResetRequest
    path: {
        passwordResetToken: string
    }
}

export type ResetPasswordResponse = void

export type ResetPasswordError = BadRequestError | ApiError | UnprocessableEntityError

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

export type GetRolesError = BadRequestError | ApiError | UnprocessableEntityError

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

export type DeleteRoleError = BadRequestError | ApiError

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

export type GetEventsError = BadRequestError | ApiError | UnprocessableEntityError

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

export type UpdateEventError = BadRequestError | ApiError | UnprocessableEntityError

export type DeleteEventData = {
    path: {
        eventId: string
    }
}

export type DeleteEventResponse = void

export type DeleteEventError = BadRequestError | ApiError

export type AddEventDayData = {
    body: EventDayRequest
    path: {
        eventId: string
    }
}

export type AddEventDayResponse = string

export type AddEventDayError = BadRequestError | ApiError | UnprocessableEntityError

export type GetEventDaysData = {
    path: {
        eventId: string
    }
    query?: {
        /**
         * Optional parameter that filters by competitionId
         */
        competitionId?: string
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

export type GetEventDaysResponse = {
    data: Array<EventDayDto>
    pagination: Pagination
}

export type GetEventDaysError = BadRequestError | ApiError | UnprocessableEntityError

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

export type UpdateEventDayError = BadRequestError | ApiError | UnprocessableEntityError

export type DeleteEventDayData = {
    path: {
        eventDayId: string
        eventId: string
    }
}

export type DeleteEventDayResponse = void

export type DeleteEventDayError = BadRequestError | ApiError

export type AssignCompetitionsToEventDayData = {
    body: AssignCompetitionsToDayRequest
    path: {
        eventDayId: string
        eventId: string
    }
}

export type AssignCompetitionsToEventDayResponse = void

export type AssignCompetitionsToEventDayError =
    | BadRequestError
    | ApiError
    | UnprocessableEntityError

export type AddCompetitionData = {
    body: CompetitionRequest
    path: {
        eventId: string
    }
}

export type AddCompetitionResponse = string

export type AddCompetitionError = BadRequestError | ApiError | UnprocessableEntityError

export type GetCompetitionsData = {
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

export type GetCompetitionsResponse = {
    data: Array<CompetitionDto>
    pagination: Pagination
}

export type GetCompetitionsError = BadRequestError | ApiError | UnprocessableEntityError

export type GetCompetitionData = {
    path: {
        competitionId: string
        eventId: string
    }
}

export type GetCompetitionResponse = CompetitionDto

export type GetCompetitionError = BadRequestError | ApiError

export type UpdateCompetitionData = {
    body: CompetitionRequest
    path: {
        competitionId: string
        eventId: string
    }
}

export type UpdateCompetitionResponse = void

export type UpdateCompetitionError = BadRequestError | ApiError | UnprocessableEntityError

export type DeleteCompetitionData = {
    path: {
        competitionId: string
        eventId: string
    }
}

export type DeleteCompetitionResponse = void

export type DeleteCompetitionError = BadRequestError | ApiError

export type AssignDaysToCompetitionData = {
    body: AssignDaysToCompetitionRequest
    path: {
        competitionId: string
        eventId: string
    }
}

export type AssignDaysToCompetitionResponse = void

export type AssignDaysToCompetitionError = BadRequestError | ApiError | UnprocessableEntityError

export type AddDocumentsData = {
    body: {
        documentType?: string
        files?: Array<Blob | File>
    }
    path: {
        eventId: string
    }
}

export type AddDocumentsResponse = void

export type AddDocumentsError = BadRequestError | ApiError | UnprocessableEntityError

export type GetDocumentsData = {
    path: {
        eventId: string
    }
    query?: {
        documentType?: string
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

export type GetDocumentsResponse = {
    data: Array<EventDocumentDto>
    pagination: Pagination
}

export type GetDocumentsError = BadRequestError | ApiError | UnprocessableEntityError

export type DownloadDocumentData = {
    path: {
        eventDocumentId: string
        eventId: string
    }
}

export type DownloadDocumentResponse = Blob | File

export type DownloadDocumentError = BadRequestError | ApiError

export type UpdateDocumentData = {
    body: EventDocumentRequest
    path: {
        eventDocumentId: string
        eventId: string
    }
}

export type UpdateDocumentResponse = void

export type UpdateDocumentError = BadRequestError | ApiError | UnprocessableEntityError

export type DeleteDocumentData = {
    path: {
        eventDocumentId: string
        eventId: string
    }
}

export type DeleteDocumentResponse = void

export type DeleteDocumentError = BadRequestError | ApiError

export type AddCompetitionTemplateData = {
    body: CompetitionTemplateRequest
}

export type AddCompetitionTemplateResponse = string

export type AddCompetitionTemplateError = BadRequestError | ApiError | UnprocessableEntityError

export type GetCompetitionTemplatesData = {
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

export type GetCompetitionTemplatesResponse = {
    data: Array<CompetitionTemplateDto>
    pagination: Pagination
}

export type GetCompetitionTemplatesError = BadRequestError | ApiError | UnprocessableEntityError

export type GetCompetitionTemplateData = {
    path: {
        competitionTemplateId: string
    }
}

export type GetCompetitionTemplateResponse = CompetitionTemplateDto

export type GetCompetitionTemplateError = BadRequestError | ApiError

export type UpdateCompetitionTemplateData = {
    body: CompetitionTemplateRequest
    path: {
        competitionTemplateId: string
    }
}

export type UpdateCompetitionTemplateResponse = void

export type UpdateCompetitionTemplateError = BadRequestError | ApiError | UnprocessableEntityError

export type DeleteCompetitionTemplateData = {
    path: {
        competitionTemplateId: string
    }
}

export type DeleteCompetitionTemplateResponse = void

export type DeleteCompetitionTemplateError = BadRequestError | ApiError

export type AddCompetitionCategoryData = {
    body: CompetitionCategoryRequest
}

export type AddCompetitionCategoryResponse = string

export type AddCompetitionCategoryError = BadRequestError | ApiError | UnprocessableEntityError

export type GetCompetitionCategoriesResponse = {
    data: Array<CompetitionCategoryDto>
    pagination: Pagination
}

export type GetCompetitionCategoriesError = BadRequestError | ApiError | UnprocessableEntityError

export type UpdateCompetitionCategoryData = {
    body: CompetitionCategoryRequest
    path: {
        competitionCategoryId: string
    }
}

export type UpdateCompetitionCategoryResponse = void

export type UpdateCompetitionCategoryError = BadRequestError | ApiError | UnprocessableEntityError

export type DeleteCompetitionCategoryData = {
    path: {
        competitionCategoryId: string
    }
}

export type DeleteCompetitionCategoryResponse = void

export type DeleteCompetitionCategoryError = BadRequestError | ApiError

export type AddNamedParticipantData = {
    body: NamedParticipantRequest
}

export type AddNamedParticipantResponse = string

export type AddNamedParticipantError = BadRequestError | ApiError | UnprocessableEntityError

export type GetNamedParticipantsResponse = {
    data: Array<NamedParticipantDto>
    pagination: Pagination
}

export type GetNamedParticipantsError = BadRequestError | ApiError | UnprocessableEntityError

export type UpdateNamedParticipantData = {
    body: NamedParticipantRequest
    path: {
        namedParticipantId: string
    }
}

export type UpdateNamedParticipantResponse = void

export type UpdateNamedParticipantError = BadRequestError | ApiError | UnprocessableEntityError

export type DeleteNamedParticipantData = {
    path: {
        namedParticipantId: string
    }
}

export type DeleteNamedParticipantResponse = void

export type DeleteNamedParticipantError = BadRequestError | ApiError

export type AddFeeData = {
    body: FeeRequest
}

export type AddFeeResponse = string

export type AddFeeError = BadRequestError | ApiError | UnprocessableEntityError

export type GetFeesResponse = {
    data: Array<FeeDto>
    pagination: Pagination
}

export type GetFeesError = BadRequestError | ApiError | UnprocessableEntityError

export type UpdateFeeData = {
    body: FeeRequest
    path: {
        feeId: string
    }
}

export type UpdateFeeResponse = void

export type UpdateFeeError = BadRequestError | ApiError | UnprocessableEntityError

export type DeleteFeeData = {
    path: {
        feeId: string
    }
}

export type DeleteFeeResponse = void

export type DeleteFeeError = BadRequestError | ApiError

export type NewCaptchaResponse = CaptchaDto

export type NewCaptchaError = ApiError

export type AddDocumentTypeData = {
    body: EventDocumentTypeRequest
}

export type AddDocumentTypeResponse = string

export type AddDocumentTypeError = BadRequestError | ApiError | UnprocessableEntityError

export type GetDocumentTypesData = {
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

export type GetDocumentTypesResponse = {
    data: Array<EventDocumentTypeDto>
    pagination: Pagination
}

export type GetDocumentTypesError = BadRequestError | ApiError | UnprocessableEntityError

export type UpdateDocumentTypeData = {
    body: EventDocumentTypeRequest
    path: {
        eventDocumentTypeId: string
    }
}

export type UpdateDocumentTypeResponse = void

export type UpdateDocumentTypeError = BadRequestError | ApiError | UnprocessableEntityError

export type DeleteDocumentTypeData = {
    path: {
        eventDocumentTypeId: string
    }
}

export type DeleteDocumentTypeResponse = void

export type DeleteDocumentTypeError = BadRequestError | ApiError
