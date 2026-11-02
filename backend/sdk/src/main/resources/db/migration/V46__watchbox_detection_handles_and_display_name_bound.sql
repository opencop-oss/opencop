-- Live alerts carry a handle to the retained detection row and the object's display name so a
-- browser can open the detection it just heard about and label the toast without a lookup.
-- Both nullable: outbox rows written before this migration and drained after it carry neither,
-- and the six identity columns remain the delivery/deduplication contract. detection_id is
-- informational: deliberately no FK to watchbox_detection, which expires independently.
ALTER TABLE watchbox.watchbox_alert_outbox
    ADD COLUMN detection_id        uuid,
    ADD COLUMN object_display_name varchar(255);

-- The display name is capped at capture from this release on (255 characters: more than a label
-- needs, but the same bound as object_id so a client may fall back to the object id without a
-- width mismatch), so an overlong value can no longer reject a detection and the retained copy
-- declares the bound too. Existing rows are display metadata inside the retention window and are
-- truncated in place.
ALTER TABLE watchbox.watchbox_detection
    ALTER COLUMN object_display_name TYPE varchar(255) USING left(object_display_name, 255);
