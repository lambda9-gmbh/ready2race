import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model RoleDto {
  // Role information
}

@route("/role")
@tag("Role")
interface RoleRoutes {
  // Role routes
}
