import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model MatchResultImportConfigDto {
  // Match result import config information
}

@route("/matchResultImportConfig")
@tag("MatchResultImportConfig")
interface MatchResultImportConfigRoutes {
  // Match result import config routes
}
