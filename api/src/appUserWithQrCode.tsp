import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model AppUserWithQrCodeDto {
  id: string; // UUID
  firstname: string;
  lastname: string;
  email: string;
  club?: string; // UUID
  roles: RoleDto[]; // Reference to role module
  qrCodeId: string;
  eventId: string; // UUID
  createdAt: utcDateTime;
  createdBy?: string; // UUID
}

// ============================================================================
// Routes
// ============================================================================

@route("/appUserWithQrCode")
@tag("AppUserWithQrCode")
interface AppUserWithQrCodeRoutes {
  // Get paginated list of app users with QR codes for event
  @get
  @route("")
  listAppUsersWithQrCode(
    @query eventId: string, // UUID - from pathParam in implementation
    // Pagination parameters
  ): AppUserWithQrCodeDto[];

  // Delete QR code
  @delete
  @route("/qrCode/{qrCodeId}")
  deleteQrCode(
    @path qrCodeId: string,
  ): void;
}