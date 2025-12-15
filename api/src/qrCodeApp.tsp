import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model QrCodeAppDto {
  // QR code app information
}

@route("/qrCodeApp")
@tag("QrCodeApp")
interface QrCodeAppRoutes {
  // QR code app routes
}
