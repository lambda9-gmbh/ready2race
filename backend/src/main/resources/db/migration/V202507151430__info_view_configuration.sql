-- Create enum type for view types
CREATE TYPE info_view_type AS ENUM ('LATEST_MATCH_RESULTS', 'UPCOMING_MATCHES');

-- Create info_view_configuration table
CREATE TABLE info_view_configuration (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    view_type info_view_type NOT NULL,
    display_duration_seconds INTEGER NOT NULL DEFAULT 10 CHECK (display_duration_seconds > 0),
    data_limit INTEGER NOT NULL DEFAULT 10 CHECK (data_limit > 0 AND data_limit <= 100),
    filters JSONB DEFAULT '{}',
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create indexes
CREATE INDEX idx_info_view_configuration_event_id ON info_view_configuration(event_id);
CREATE INDEX idx_info_view_configuration_sort_order ON info_view_configuration(event_id, sort_order) WHERE is_active = true;

-- Add comments
COMMENT ON TABLE info_view_configuration IS 'Stores configuration for event info page views';
COMMENT ON COLUMN info_view_configuration.view_type IS 'Type of information to display in this view';
COMMENT ON COLUMN info_view_configuration.display_duration_seconds IS 'How long to display this view before rotating';
COMMENT ON COLUMN info_view_configuration.data_limit IS 'Maximum number of items to show in this view';
COMMENT ON COLUMN info_view_configuration.filters IS 'Type-specific filters as JSON (e.g., competitionType, eventDayId)';
COMMENT ON COLUMN info_view_configuration.sort_order IS 'Order in which views are displayed during rotation';