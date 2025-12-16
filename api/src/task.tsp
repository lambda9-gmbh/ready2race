import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

model TaskDto {
  // Task information
}

@route("/task")
@tag("Task")
interface TaskRoutes {
  // Task routes
}
