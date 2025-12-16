import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model TimecodeDto {
  // Timecode information
}

@route("/timecode")
@tag("Timecode")
interface TimecodeRoutes {
  // Timecode routes
}
