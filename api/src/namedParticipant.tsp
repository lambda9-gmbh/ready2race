import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model NamedParticipantDto {
  // Named participant information
}

@route("/namedParticipant")
@tag("NamedParticipant")
interface NamedParticipantRoutes {
  // Named participant routes
}
