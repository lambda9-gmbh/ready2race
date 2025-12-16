import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model CompetitionDeregistrationDto {
  competitionSetupRoundId?: string; // UUID
  reason?: string;
}

model CompetitionDeregistrationRequest {
  reason?: string;
}

// ============================================================================
// Routes
// ============================================================================

@route("/deregistration")
@tag("CompetitionDeregistration")
interface CompetitionDeregistrationRoutes {
  // Create competition deregistration
  @post
  @route("")
  createDeregistration(
    @query competitionId: string, // UUID - from pathParam
    @query competitionRegistrationId: string, // UUID - from pathParam
    @body request: CompetitionDeregistrationRequest,
  ): CompetitionDeregistrationDto;

  // Remove competition deregistration
  @delete
  @route("")
  removeDeregistration(
    @query competitionId: string, // UUID - from pathParam
    @query competitionRegistrationId: string, // UUID - from pathParam
  ): void;
}
