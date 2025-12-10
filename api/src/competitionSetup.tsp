import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model CompetitionSetupDto {
  // Competition setup information
}

// ============================================================================
// Routes
// ============================================================================

@route("/competitionSetup")
@tag("CompetitionSetup")
interface CompetitionSetupRoutes {
  // Update competition setup
  @put
  @route("")
  updateSetup(
    @body request: CompetitionSetupDto,
  ): CompetitionSetupDto;

  // Get competition setup
  @get
  @route("")
  getSetup(): CompetitionSetupDto;
}
