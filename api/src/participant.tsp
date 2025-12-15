import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model ParticipantDto {
  // Participant information
}

@route("/participant")
@tag("Participant")
interface ParticipantRoutes {
  // Participant routes
}
