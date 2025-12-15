import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model SequenceDto {
  // Sequence information
}

@route("/sequence")
@tag("Sequence")
interface SequenceRoutes {
  // Sequence routes
}
