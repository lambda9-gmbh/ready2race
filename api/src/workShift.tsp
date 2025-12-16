import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model WorkShiftDto {
  // Work shift information
}

@route("/workShift")
@tag("WorkShift")
interface WorkShiftRoutes {
  // Work shift routes
}
