import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model CaptchaChallengeDto {
  id: string; // UUID
  imgSrc: string;
  solutionMin: int32;
  solutionMax: int32;
  handleToHeightRatio: float32;
  start: int32;
}

// ============================================================================
// Routes
// ============================================================================

@route("/captcha")
@tag("Captcha")
interface CaptchaRoutes {
  // Create new captcha challenge
  @post
  @route("")
  newChallenge(): CaptchaChallengeDto;
}
