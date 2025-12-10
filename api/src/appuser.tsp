import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

enum EmailLanguage {
  EN,
  DE,
}

enum Gender {
  M,
  F,
  D,
}

model AppUserDto {
  id: string; // UUID
  email: string;
  firstname: string;
  lastname: string;
  roles: RoleDto[]; // Reference to role module
  qrCodeId?: string;
}

model AppUserNameDto {
  id: string; // UUID
  firstname: string;
  lastname: string;
}

model AppUserForEventDto {
  id: string; // UUID
  firstname: string;
  lastname: string;
  email: string;
  club?: string; // UUID
  qrCodeId?: string;
}

model AppUserRegistrationDto {
  id: string; // UUID
  email: string;
  firstname: string;
  lastname: string;
  language: EmailLanguage;
  expiresAt: utcDateTime;
  createdAt: utcDateTime;
  assignedEmail?: AssignedEmailDto;
}

model AppUserInvitationDto {
  id: string; // UUID
  email: string;
  firstname: string;
  lastname: string;
  language: EmailLanguage;
  expiresAt: utcDateTime;
  createdAt: utcDateTime;
  assignedEmail?: AssignedEmailDto;
  roles: RoleDto[];
  createdBy?: AppUserNameDto;
}

model PendingClubRepresentativeApprovalDto {
  userId: string; // UUID
  firstName: string;
  lastName: string;
  email: string;
  createdAt: utcDateTime;
}

model AppUserRegisterRequest {
  email: string;
  password: string;
  firstname: string;
  lastname: string;
  clubId?: string; // UUID
  clubname?: string;
  language: EmailLanguage;
  callbackUrl: string;
  registerToSingleCompetitions: ParticipantRegisterCompetitionRequest[];
  birthYear?: int32;
  gender?: Gender;
}

model UpdateAppUserRequest {
  firstname: string;
  lastname: string;
  roles: string[]; // UUID[]
}

model InviteRequest {
  email: string;
  firstname: string;
  lastname: string;
  language: EmailLanguage;
  roles: string[]; // UUID[]
  admin?: boolean;
  callbackUrl: string;
}

model AcceptInvitationRequest {
  token: string;
  password: string;
}

model VerifyRegistrationRequest {
  token: string;
  callbackUrl: string;
}

model PasswordResetInitRequest {
  email: string;
  language: EmailLanguage;
  callbackUrl: string;
}

model PasswordResetRequest {
  password: string;
}

@route("/user")
@tag("AppUser")
interface UserRoutes {
  // Get paginated list of users
  @get
  @route("")
  listUsers(
    @query noClub?: boolean,
    // Pagination parameters
  ): AppUserDto[];

  // Get user by ID
  @get
  @route("/{userId}")
  getUser(
    @path userId: string, // UUID
  ): AppUserDto;

  // Update user
  @put
  @route("/{userId}")
  updateUser(
    @path userId: string, // UUID
    @body request: UpdateAppUserRequest,
  ): AppUserDto;

  // Get tasks for user
  @get
  @route("/{userId}/task")
  getUserTasks(
    @path userId: string, // UUID
    // Pagination parameters
  ): TaskWithResponsibleUsersDto[]; // Reference to task module

  // Get work shifts for user
  @get
  @route("/{userId}/workshift")
  getUserWorkShifts(
    @path userId: string, // UUID
    @query timeFrom?: utcDateTime,
    @query timeTo?: utcDateTime,
    // Pagination parameters
  ): WorkShiftWithAssignedUsersDto[]; // Reference to workShift module

  // Approve/reject club representative
  @put
  @route("/{userId}/clubRepresentative/approval")
  updateClubRepresentativeApproval(
    @path userId: string, // UUID
    @query approve: boolean,
  ): void;
}

@route("/user/registration")
interface RegistrationRoutes {
  // Register new user
  @post
  @route("")
  register(
    @body request: AppUserRegisterRequest,
  ): void;

  // Get paginated registrations
  @get
  @route("")
  listRegistrations(
    // Pagination parameters
  ): AppUserRegistrationDto[];

  // Verify registration
  @post
  @route("/verify")
  verifyRegistration(
    @body request: VerifyRegistrationRequest,
  ): void;
}

@route("/user/invitation")
interface InvitationRoutes {
  // Invite user
  @post
  @route("")
  invite(
    @body request: InviteRequest,
  ): void;

  // Get paginated invitations
  @get
  @route("")
  listInvitations(
    // Pagination parameters
  ): AppUserInvitationDto[];

  // Accept invitation
  @post
  @route("/accept")
  acceptInvitation(
    @body request: AcceptInvitationRequest,
  ): void;
}

@route("/user/resetPassword")
interface PasswordResetRoutes {
  // Initiate password reset
  @post
  @route("")
  initPasswordReset(
    @body request: PasswordResetInitRequest,
  ): void;

  // Reset password with token
  @put
  @route("/{passwordResetToken}")
  resetPassword(
    @path passwordResetToken: string,
    @body request: PasswordResetRequest,
  ): void;
}

@route("/user/event/{eventId}")
interface EventUserRoutes {
  // Get all users for event
  @get
  @route("")
  getUsersForEvent(
    @path eventId: string, // UUID
    // Pagination parameters
  ): AppUserForEventDto[];
}