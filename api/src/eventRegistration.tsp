import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model EventRegistrationDto {
  // Event registration information
}

@route("/eventRegistration")
@tag("EventRegistration")
interface EventRegistrationRoutes {
  // Event registration routes
}
