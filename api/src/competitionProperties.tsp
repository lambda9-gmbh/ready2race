import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model CompetitionPropertiesDto {
  identifier: string;
  name: string;
  shortName?: string;
  description?: string;
  competitionCategory?: CompetitionCategoryDto; // Reference to competitionCategory module
  namedParticipants: NamedParticipantForCompetitionDto[];
  fees: FeeForCompetitionDto[];
  lateRegistrationAllowed: boolean;
  challengeConfig?: CompetitionChallengeConfigDto;
  ratingCategoryRequired: boolean;
}

model CompetitionPropertiesRequest {
  identifier: string;
  name: string;
  shortName?: string;
  description?: string;
  competitionCategory?: string; // UUID
  namedParticipants: NamedParticipantForCompetitionRequestDto[];
  fees: FeeForCompetitionRequestDto[];
  lateRegistrationAllowed: boolean;
  setupTemplate?: string; // UUID
  challengeConfig?: CompetitionChallengeConfigRequest;
  ratingCategoryRequired: boolean;
}

model NamedParticipantForCompetitionDto {
  // Named participant information
}

model NamedParticipantForCompetitionRequestDto {
  namedParticipant: string; // UUID
}

model FeeForCompetitionDto {
  // Fee information
}

model FeeForCompetitionRequestDto {
  fee: string; // UUID
}

model CompetitionChallengeConfigDto {
  // Challenge configuration
}

model CompetitionChallengeConfigRequest {
  // Challenge configuration request
}

// Note: This module has no dedicated routes - properties are managed through the competition module
