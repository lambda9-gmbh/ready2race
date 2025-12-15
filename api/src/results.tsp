import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model ResultDto {
  // Result information
}

@route("/results")
@tag("Results")
interface ResultRoutes {
  // Result routes
}
