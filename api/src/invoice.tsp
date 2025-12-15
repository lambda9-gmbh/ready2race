import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model InvoiceDto {
  // Invoice information
}

@route("/invoice")
@tag("Invoice")
interface InvoiceRoutes {
  // Invoice routes
}
