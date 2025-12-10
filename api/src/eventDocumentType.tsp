import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model EventDocumentTypeDto {
  // Event document type information
}

@route("/eventDocumentType")
@tag("EventDocumentType")
interface EventDocumentTypeRoutes {
  // Event document type routes
}
