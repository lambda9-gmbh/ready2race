import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model RatingCategoryDto {
  // Rating category information
}

@route("/ratingcategory")
@tag("Ratingcategory")
interface RatingCategoryRoutes {
  // Rating category routes
}
