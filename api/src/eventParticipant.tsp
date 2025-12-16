import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model EventParticipantDto {
  // Event participant information
}

@route("/eventParticipant")
@tag("EventParticipant")
interface EventParticipantRoutes {
  // Event participant routes
}
