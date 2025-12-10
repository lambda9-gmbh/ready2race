import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model ParticipantRequirementDto {
  // Participant requirement information
}

@route("/participantRequirement")
@tag("ParticipantRequirement")
interface ParticipantRequirementRoutes {
  // Participant requirement routes
}
