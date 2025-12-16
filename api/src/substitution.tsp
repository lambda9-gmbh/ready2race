import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model SubstitutionDto {
  // Substitution information
}

@route("/substitution")
@tag("Substitution")
interface SubstitutionRoutes {
  // Substitution routes
}
