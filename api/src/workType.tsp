import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model WorkTypeDto {
  // Work type information
}

@route("/workType")
@tag("WorkType")
interface WorkTypeRoutes {
  // Work type routes
}
