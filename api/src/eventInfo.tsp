import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model EventInfoDto {
  // Event info information
}

@route("/eventInfo")
@tag("EventInfo")
interface EventInfoRoutes {
  // Event info routes
}
