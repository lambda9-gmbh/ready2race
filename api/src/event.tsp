import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model EventDto {
  // Event information
}

model CreateEventRequest {
  // Event creation data
}

model UpdateEventRequest {
  // Event update data
}

// ============================================================================
// Routes
// ============================================================================

@route("/event")
@tag("Event")
interface EventRoutes {
  // Create event
  @post
  @route("")
  createEvent(
    @body request: CreateEventRequest,
  ): EventDto;

  // Get paginated events
  @get
  @route("")
  listEvents(
    // Pagination parameters
  ): EventDto[];

  // Get public events
  @get
  @route("/public")
  listPublicEvents(
    // Pagination parameters
  ): EventDto[];

  // Get event by ID
  @get
  @route("/{eventId}")
  getEvent(
    @path eventId: string, // UUID
  ): EventDto;

  // Update event
  @put
  @route("/{eventId}")
  updateEvent(
    @path eventId: string, // UUID
    @body request: UpdateEventRequest,
  ): EventDto;

  // Delete event
  @delete
  @route("/{eventId}")
  deleteEvent(
    @path eventId: string, // UUID
  ): void;
}
