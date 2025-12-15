import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model FeeDto {
  // Fee information
}

@route("/fee")
@tag("Fee")
interface FeeRoutes {
  // Fee routes
}
