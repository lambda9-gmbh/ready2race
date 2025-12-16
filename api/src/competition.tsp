import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model CompetitionDto {
  id: string; // UUID
  event: string; // UUID
  properties: CompetitionPropertiesDto; // Reference to competitionProperties module
  registrationCount: int64;
}

model CompetitionForExportDto {
  id: string; // UUID
  identifier: string;
  name: string;
}

model CompetitionsForRegistrationDto {
  competitions: CompetitionDto[];
  teamsEventOmitted: boolean;
}

model AssignDaysToCompetitionRequest {
  days: string[]; // UUID[]
}

// ============================================================================
// Routes
// ============================================================================

@route("/competition")
@tag("Competition")
interface CompetitionRoutes {
  // Create competition
  @post
  @route("")
  addCompetition(
    @query eventId: string, // UUID - from pathParam
    @body request: CompetitionPropertiesRequest, // Reference to competitionProperties module
  ): CompetitionDto;

  // Get paginated competitions for event
  @get
  @route("")
  listCompetitions(
    @query eventId: string, // UUID - from pathParam
    @query eventDayId?: string, // UUID
    // Pagination parameters
  ): CompetitionDto[];

  // Get competitions for registration
  @get
  @route("/registration")
  getForRegistration(
    @query eventId: string, // UUID - from pathParam
    @query birthYear: int32,
    @query gender: string, // Gender enum
  ): CompetitionsForRegistrationDto;

  // Get competition by ID with properties
  @get
  @route("/{competitionId}")
  getCompetition(
    @path competitionId: string, // UUID
  ): CompetitionDto;

  // Update competition
  @put
  @route("/{competitionId}")
  updateCompetition(
    @path competitionId: string, // UUID
    @query eventId: string, // UUID - from pathParam
    @body request: CompetitionPropertiesRequest, // Reference to competitionProperties module
  ): CompetitionDto;

  // Delete competition
  @delete
  @route("/{competitionId}")
  deleteCompetition(
    @path competitionId: string, // UUID
  ): void;

  // Assign days to competition
  @put
  @route("/{competitionId}/days")
  assignDays(
    @path competitionId: string, // UUID
    @body request: AssignDaysToCompetitionRequest,
  ): void;
}
