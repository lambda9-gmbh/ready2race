import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model EventDocumentDto {
  // Event document information
}

@route("/eventDocument")
@tag("EventDocument")
interface EventDocumentRoutes {
  // Event document routes
}
