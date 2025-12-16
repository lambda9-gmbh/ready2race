import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model EventDayDto {
  // Event day information
}

@route("/eventDay")
@tag("EventDay")
interface EventDayRoutes {
  // Event day routes
}
