import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model CompetitionExecutionProgressDto {
  rounds: CompetitionRoundDto[];
  canNotCreateRoundReasons: string[]; // CompetitionExecutionCanNotCreateRoundReason enum
  isChallengeEvent: boolean;
}

model CompetitionRoundDto {
  setupRoundId: string; // UUID
  name: string;
  matches: CompetitionMatchDto[];
  required: boolean;
  substitutions: SubstitutionDto[]; // Reference to substitution module
}

model CompetitionMatchDto {
  id: string; // UUID
  name?: string;
  teams: CompetitionMatchTeamDto[];
  weighting: int32;
  executionOrder: int32;
  startTime?: utcDateTime;
  startTimeOffset?: int64;
  currentlyRunning: boolean;
}

model CompetitionMatchTeamDto {
  // Team information
}

model CompetitionTeamPlaceDto {
  competitionRegistrationId: string; // UUID
  teamNumber: int32;
  teamName?: string;
  clubId: string; // UUID
  clubName: string;
  namedParticipants: CompetitionTeamNamedParticipantDto[];
  place: int32;
  deregistered: boolean;
  deregistrationReason?: string;
}

model CompetitionTeamNamedParticipantDto {
  // Named participant information
}

model UpdateCompetitionMatchRequest {
  startTime?: utcDateTime;
  teams: UpdateCompetitionMatchTeamRequest[];
}

model UpdateCompetitionMatchTeamRequest {
  startNumber: int32;
  // Additional fields
}

model UpdateCompetitionMatchResultRequest {
  teamResults: UpdateCompetitionMatchTeamResultRequest[];
}

model UpdateCompetitionMatchTeamResultRequest {
  registrationId: string; // UUID
  place?: int32;
  timeString?: string;
  failed?: boolean;
}

model UpdateCompetitionMatchRunningStateRequest {
  // Running state fields
}

model UploadMatchResultRequest {
  // Upload configuration
}

model CompetitionChallengeResultRequest {
  // Challenge result fields
}

enum StartListFileType {
  PDF,
  CSV,
}

// ============================================================================
// Routes
// ============================================================================

@route("/competitionExecution")
@tag("CompetitionExecution")
interface CompetitionExecutionRoutes {
  // Get execution progress
  @get
  @route("")
  getProgress(
    @query eventId: string, // UUID - from pathParam
    @query competitionId: string, // UUID - from pathParam
  ): CompetitionExecutionProgressDto;

  // Delete current round
  @delete
  @route("")
  deleteCurrentRound(
    @query eventId: string, // UUID - from pathParam
    @query competitionId: string, // UUID - from pathParam
  ): void;

  // Create next round
  @post
  @route("/createNextRound")
  createNextRound(
    @query eventId: string, // UUID - from pathParam
    @query competitionId: string, // UUID - from pathParam
  ): void;

  // Update match data
  @put
  @route("/{competitionMatchId}/data")
  updateMatchData(
    @path competitionMatchId: string, // UUID
    @query eventId: string, // UUID - from pathParam
    @body request: UpdateCompetitionMatchRequest,
  ): void;

  // Update match running state
  @put
  @route("/{competitionMatchId}/running-state")
  updateMatchRunningState(
    @path competitionMatchId: string, // UUID
    @query eventId: string, // UUID - from pathParam
    @body request: UpdateCompetitionMatchRunningStateRequest,
  ): void;

  // Update match result
  @put
  @route("/{competitionMatchId}/results")
  updateMatchResult(
    @path competitionMatchId: string, // UUID
    @query eventId: string, // UUID - from pathParam
    @query competitionId: string, // UUID - from pathParam
    @body request: UpdateCompetitionMatchResultRequest,
  ): void;

  // Upload match result file
  @put
  @route("/{competitionMatchId}/results-file")
  uploadMatchResultFile(
    @path competitionMatchId: string, // UUID
    @query eventId: string, // UUID - from pathParam
    @query competitionId: string, // UUID - from pathParam
    @header contentType: "multipart/form-data",
    // File upload with UploadMatchResultRequest
  ): void;

  // Download start list
  @get
  @route("/{competitionMatchId}/startList")
  downloadStartList(
    @path competitionMatchId: string, // UUID
    @query eventId: string, // UUID - from pathParam
    @query fileType: StartListFileType,
    @query config?: string, // UUID - for CSV type
  ): bytes; // File download

  // Get competition places
  @get
  @route("/places")
  getCompetitionPlaces(
    @query eventId: string, // UUID - from pathParam
    @query competitionId: string, // UUID - from pathParam
  ): CompetitionTeamPlaceDto[];

  // Save challenge result (with access token)
  @post
  @route("/challenge/team-results/{competitionRegistrationId}/accessToken/{accessToken}")
  saveChallengeResultWithToken(
    @path competitionRegistrationId: string, // UUID
    @path accessToken: string,
    @query competitionId: string, // UUID - from pathParam
    @header contentType: "multipart/form-data",
    // File upload with CompetitionChallengeResultRequest
  ): void;

  // Save challenge result (authenticated)
  @post
  @route("/challenge/team-results/{competitionRegistrationId}")
  saveChallengeResult(
    @path competitionRegistrationId: string, // UUID
    @query competitionId: string, // UUID - from pathParam
    @header contentType: "multipart/form-data",
    // File upload with CompetitionChallengeResultRequest
  ): void;

  // Delete challenge result
  @delete
  @route("/challenge/team-results/{competitionRegistrationId}")
  deleteChallengeResult(
    @path competitionRegistrationId: string, // UUID
    @query competitionId: string, // UUID - from pathParam
  ): void;

  // Verify challenge result
  @put
  @route("/challenge/team-results/{competitionRegistrationId}/verify")
  verifyChallengeResult(
    @path competitionRegistrationId: string, // UUID
    @query competitionId: string, // UUID - from pathParam
  ): void;

  // Download result document (authenticated)
  @get
  @route("/result-document/{resultDocumentId}")
  downloadResultDocument(
    @path resultDocumentId: string, // UUID
  ): bytes; // File download

  // Download result document (with access token)
  @get
  @route("/result-document/{resultDocumentId}/accessToken/{accessToken}")
  downloadResultDocumentWithToken(
    @path resultDocumentId: string, // UUID
    @path accessToken: string,
  ): bytes; // File download
}
