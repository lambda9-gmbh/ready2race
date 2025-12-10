import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model GlobalConfigurationDto {
  // Global configuration information
}

@route("/globalConfigurations")
@tag("GlobalConfigurations")
interface GlobalConfigurationsRoutes {
  // Global configurations routes
}
