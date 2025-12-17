import "@typespec/http";
import "@typespec/openapi";

using Http;
using OpenAPI;
namespace Ready2Race;

model ClubDto {
  id: uuid;
  name: string;
  createdAt: dateTime;
  updatedAt: dateTime;
}

model ClubSearchDto {
  id: uuid;
  name: string;
}

model ClubUpsertDto {
  name: string;
}

model ClubImportRequest {
  separator: string;
  charset: string;
  colName: string;
  noHeader: boolean;
}

@route("/club")
@tag("Club")
interface ClubRoutes {
  @post
  @operationId("")
  addClub(
    @body request: ClubUpsertDto,
  ): ClubDto;

  // Get paginated clubs
  @get
  @operationId("")
  listClubs(
    // Pagination parameters
  ): ClubDto[];

  // Search clubs
  @get
  @route("/search")
  @operationId("")
  searchClubs(
    @query eventId?: string, // UUID
    // Pagination parameters
  ): ClubSearchDto[];

  // Get club by ID
  @get
  @route("/{clubId}")
  @operationId("")
  getClub(
    @path clubId: string, // UUID
  ): ClubDto;

  // Update club
  @put
  @route("/{clubId}")
  @operationId("")
  updateClub(
    @path clubId: string, // UUID
    @body request: ClubUpsertDto,
  ): ClubDto;

  // Delete club
  @delete
  @route("/{clubId}")
  @operationId("")
  deleteClub(
    @path clubId: string, // UUID
  ): void;

  // Get all users by club ID
  @get
  @route("/{clubId}/user")
  @operationId("")
  getUsersByClub(
    @path clubId: string, // UUID
  ): AppUserDto[]; // Reference to appuser module

  // Get pending club representative approvals
  @get
  @route("/{clubId}/clubRepresentative")
  @operationId("")
  getPendingApprovals(
    @path clubId: string, // UUID
  ): PendingClubRepresentativeApprovalDto[]; // Reference to appuser module

  // Import clubs from CSV
  @post
  @route("/import")
  @operationId("")
  importClubs(
    @header contentType: "multipart/form-data",
    // File upload with ClubImportRequest
  ): void;
}
