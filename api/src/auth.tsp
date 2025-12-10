import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model LoginDto {
  id: string; // UUID
  clubId?: string; // UUID
  privileges: PrivilegeDto[];
}

model PrivilegeDto {
  id: string; // UUID
  action: string;
  resource: string;
  scope: string;
}

model LoginRequest {
  email: string;
  password: string;
}

// ============================================================================
// Enums
// ============================================================================

enum PrivilegeAction {
  CREATE,
  READ,
  UPDATE,
  DELETE,
}

enum PrivilegeResource {
  USER,
  EVENT,
  CLUB,
  REGISTRATION,
  INVOICE,
  SUBSTITUTION,
  APP_EVENT_REQUIREMENT,
  APP_QR_MANAGEMENT,
  APP_COMPETITION_CHECK,
  APP_CATERER,
  WEB_DAV,
  RESULT,
  ADMINISTRATION,
}

enum PrivilegeScope {
  OWN,
  GLOBAL,
}

// ============================================================================
// Routes
// ============================================================================

@route("/login")
@tag("Auth")
interface LoginRoutes {
  // Login
  @post
  @route("")
  login(
    @body request: LoginRequest,
  ): LoginDto;

  // Check login status
  @get
  @route("")
  checkLogin(): LoginDto;

  // Logout
  @delete
  @route("")
  logout(): void;
}

@route("/privileges")
@tag("Auth")
interface PrivilegeRoutes {
  // Get all privileges
  @get
  @route("")
  getAllPrivileges(): PrivilegeDto[];
}