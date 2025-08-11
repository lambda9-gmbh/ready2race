-- Add named_participant and qr_code_required columns to event_has_participant_requirement table

-- Add the new columns
ALTER TABLE event_has_participant_requirement
    ADD COLUMN named_participant UUID REFERENCES named_participant(id),
    ADD COLUMN qr_code_required BOOLEAN NOT NULL DEFAULT FALSE;

-- Drop the existing primary key to create a new unique constraint
ALTER TABLE event_has_participant_requirement
    DROP CONSTRAINT event_has_participant_requirement_pkey;

-- Create unique constraint that handles nullable named_participant
-- This allows multiple null values in the named_participant column
CREATE UNIQUE INDEX event_has_participant_requirement_unique 
    ON event_has_participant_requirement (event, participant_requirement, named_participant)
    WHERE named_participant IS NOT NULL;

-- Create another unique constraint for global requirements (where named_participant is NULL)
CREATE UNIQUE INDEX event_has_participant_requirement_unique_global
    ON event_has_participant_requirement (event, participant_requirement)
    WHERE named_participant IS NULL;

-- Add index for better query performance
CREATE INDEX idx_event_has_participant_requirement_named_participant
    ON event_has_participant_requirement(event, named_participant)
    WHERE named_participant IS NOT NULL;

-- Add comment to explain the structure
COMMENT ON COLUMN event_has_participant_requirement.named_participant IS 
    'Optional reference to named participant. If NULL, requirement applies to all participants. If set, requirement only applies to this specific named participant type.';

COMMENT ON COLUMN event_has_participant_requirement.qr_code_required IS 
    'Indicates whether QR code is required for this named participant type in this event.';