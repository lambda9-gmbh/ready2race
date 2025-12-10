import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model WebDAVDto {
  // WebDAV information
}

@route("/webDAV")
@tag("WebDAV")
interface WebDAVRoutes {
  // WebDAV routes
}
