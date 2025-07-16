CREATE TABLE team_tracking (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    competition_registration_id UUID NOT NULL REFERENCES competition_registration(id),
    event_id UUID NOT NULL REFERENCES event(id),
    scan_type VARCHAR(10) NOT NULL CHECK (scan_type IN ('ENTRY', 'EXIT')),
    scanned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scanned_by UUID REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_team_tracking_competition ON team_tracking(competition_registration_id);
CREATE INDEX idx_team_tracking_event ON team_tracking(event_id);
CREATE INDEX idx_team_tracking_scanned_at ON team_tracking(scanned_at DESC);