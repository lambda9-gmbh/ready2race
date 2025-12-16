import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model ParticipantTrackingDto {
  // Participant tracking information
}

@route("/participantTracking")
@tag("ParticipantTracking")
interface ParticipantTrackingRoutes {
  // Participant tracking routes
}
