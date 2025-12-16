import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model StartListConfigDto {
  // Start list config information
}

@route("/startListConfig")
@tag("StartListConfig")
interface StartListConfigRoutes {
  // Start list config routes
}
