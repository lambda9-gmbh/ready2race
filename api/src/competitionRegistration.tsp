import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model CompetitionRegistrationDto {
  // Competition registration information
}

model CompetitionRegistrationTeamDto {
  // Team information
}

model CompetitionRegistrationTeamUpsertDto {
  // Team upsert data
}

model CompetitionRegistrationRequestProperties {
  // Request properties
}

// ============================================================================
// Routes
// ============================================================================

@route("/competitionRegistration")
@tag("CompetitionRegistration")
interface CompetitionRegistrationRoutes {
  // Get paginated competition registrations
  @get
  @route("")
  listRegistrations(
    @query competitionId: string, // UUID - from pathParam
    // Pagination parameters
  ): CompetitionRegistrationDto[];

  // Create competition registration
  @post
  @route("")
  createRegistration(
    @query competitionId: string, // UUID - from pathParam
    @query eventId: string, // UUID - from pathParam
    @query registrationType?: string, // RegistrationInvoiceType enum
    @body request: CompetitionRegistrationTeamUpsertDto,
  ): CompetitionRegistrationDto;

  // Update competition registration
  @put
  @route("/{competitionRegistrationId}")
  updateRegistration(
    @path competitionRegistrationId: string, // UUID
    @query competitionId: string, // UUID - from pathParam
    @query eventId: string, // UUID - from pathParam
    @query registrationType?: string, // RegistrationInvoiceType enum
    @body request: CompetitionRegistrationTeamUpsertDto,
  ): CompetitionRegistrationDto;

  // Delete competition registration
  @delete
  @route("/{competitionRegistrationId}")
  deleteRegistration(
    @path competitionRegistrationId: string, // UUID
    @query competitionId: string, // UUID - from pathParam
    @query eventId: string, // UUID - from pathParam
  ): void;
}
