import "@typespec/http";
import "@typespec/openapi";

using Http;
using OpenAPI;
namespace Ready2Race;

enum Action {
  CREATE;
  READ;
  UPDATE;
  DELETE;
}

enum Resource {
  USER;
  EVENT;
  CLUB;
  REGISTRATION;
  INVOICE;
  SUBSTITUTION;
  APP_EVENT_REQUIREMENT;
  APP_QR_MANAGEMENT;
  APP_COMPETITION_CHECK;
  APP_CATERER;
  WEB_DAV;
  RESULT;
  ADMINISTRATION;
}

enum Scope {
  OWN;
  GLOBAL;
}

model PrivilegeDto {
  id: uuid;
  action: Action;
  resource: Resource;
  scope: Scope;
}

model LoginRequest {
  email: string;
  password: string;
}

model LoginDto {
  id: uuid;
  clubId?: uuid;
  privileges: PrivilegeDto[];
}

@tag("Auth")
interface AuthRoutes {

  @post
  @route("/login")
  @operationId("userLogin")
  op login(
    @body request: LoginRequest;
  ): OK<LoginDto> | BadRequest | Unauthorized | UnprocessableContent | TooManyRequests;

  @get
  @route("/login")
  @operationId("checkUserLogin")
  op checkLogin(): OK<LoginDto> | NoContent;

  @delete
  @route("/login")
  @operationId("userLogout")
  op logout(): NoContent;

  @get
  @route("/privileges")
  @operationId("getPrivileges")
  op getAllPrivileges(): OK<PrivilegeDto[]> | Unauthorized | Forbidden;
}
