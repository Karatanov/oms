-- The UI column is already labelled "Region", therefore the redundant
-- "область" suffix must not be persisted in project/subproject values.
-- Keep the Ukrainian place name itself unchanged for map and filter matching.
UPDATE projects
SET region = LEFT(TRIM(region), CHAR_LENGTH(TRIM(region)) - CHAR_LENGTH(' область'))
WHERE TRIM(region) LIKE '% область';

UPDATE projects
SET region = LEFT(TRIM(region), CHAR_LENGTH(TRIM(region)) - CHAR_LENGTH(' oblast'))
WHERE LOWER(TRIM(region)) LIKE '% oblast';
