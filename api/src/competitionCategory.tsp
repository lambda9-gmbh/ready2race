import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model CompetitionCategoryDto {
  id: string; // UUID
  name: string;
  description?: string;
}

model CompetitionCategoryRequest {
  name: string;
  description?: string;
}

// ============================================================================
// Routes
// ============================================================================

@route("/competitionCategory")
@tag("CompetitionCategory")
interface CompetitionCategoryRoutes {
  // Create competition category
  @post
  @route("")
  addCompetitionCategory(
    @body request: CompetitionCategoryRequest,
  ): CompetitionCategoryDto;

  // Get paginated competition categories
  @get
  @route("")
  listCompetitionCategories(
    // Pagination parameters
  ): CompetitionCategoryDto[];

  // Update competition category
  @put
  @route("/{competitionCategoryId}")
  updateCompetitionCategory(
    @path competitionCategoryId: string, // UUID
    @body request: CompetitionCategoryRequest,
  ): CompetitionCategoryDto;

  // Delete competition category
  @delete
  @route("/{competitionCategoryId}")
  deleteCompetitionCategory(
    @path competitionCategoryId: string, // UUID
  ): void;
}
