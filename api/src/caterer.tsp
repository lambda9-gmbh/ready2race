import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model CatererTransactionViewDto {
  id: string; // UUID
  catererId: string; // UUID
  catererFirstname: string;
  catererLastname: string;
  appUserId: string; // UUID
  userFirstname: string;
  userLastname: string;
  eventId: string; // UUID
  price: decimal;
  createdAt: utcDateTime;
}

model CatererTransactionRequest {
  appUserId: string; // UUID
  price: decimal;
  eventId: string; // UUID
}

// ============================================================================
// Routes
// ============================================================================

@route("/caterer")
@tag("Caterer")
interface CatererRoutes {
  // Create catering transaction
  @post
  @route("/transactions")
  createTransaction(
    @body request: CatererTransactionRequest,
  ): CatererTransactionViewDto;
}
