import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model SmtpConfigOverrideDto {
  // SMTP configuration
}

// ============================================================================
// Routes
// ============================================================================

@route("/smtp-override")
@tag("Email")
interface EmailRoutes {
  // Get SMTP config override
  @get
  @route("")
  getSmtpOverride(): SmtpConfigOverrideDto;

  // Set SMTP config override
  @put
  @route("")
  setSmtpOverride(
    @body request: SmtpConfigOverrideDto,
  ): SmtpConfigOverrideDto;

  // Delete SMTP config override
  @delete
  @route("")
  deleteSmtpOverride(): void;
}
