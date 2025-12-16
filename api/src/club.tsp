import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model ClubDto {
  id: string; // UUID
  name: string;
  createdAt: utcDateTime;
  updatedAt: utcDateTime;
}

model ClubSearchDto {
  id: string; // UUID
  name: string;
}

model ClubUpsertDto {
  name: string;
}

model ClubImportRequest {
  separator: string; // char
  charset: string;
  colName: string;
  noHeader: boolean;
}

// ============================================================================
// Routes
// ============================================================================

@route("/club")
@tag("Club")
interface ClubRoutes {
  // Create club
  @post
  @route("")
  addClub(
    @body request: ClubUpsertDto,
  ): ClubDto;

  // Get paginated clubs
  @get
  @route("")
  listClubs(
    // Pagination parameters
  ): ClubDto[];

  // Search clubs
  @get
  @route("/search")
  searchClubs(
    @query eventId?: string, // UUID
    // Pagination parameters
  ): ClubSearchDto[];

  // Get club by ID
  @get
  @route("/{clubId}")
  getClub(
    @path clubId: string, // UUID
  ): ClubDto;

  // Update club
  @put
  @route("/{clubId}")
  updateClub(
    @path clubId: string, // UUID
    @body request: ClubUpsertDto,
  ): ClubDto;

  // Delete club
  @delete
  @route("/{clubId}")
  deleteClub(
    @path clubId: string, // UUID
  ): void;

  // Get all users by club ID
  @get
  @route("/{clubId}/user")
  getUsersByClub(
    @path clubId: string, // UUID
  ): AppUserDto[]; // Reference to appuser module

  // Get pending club representative approvals
  @get
  @route("/{clubId}/clubRepresentative")
  getPendingApprovals(
    @path clubId: string, // UUID
  ): PendingClubRepresentativeApprovalDto[]; // Reference to appuser module

  // Import clubs from CSV
  @post
  @route("/import")
  importClubs(
    @header contentType: "multipart/form-data",
    // File upload with ClubImportRequest
  ): void;
}
