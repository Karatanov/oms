-- The imported operational workbook contains addresses for every subproject,
-- but a portion of the rows has no geocoded latitude/longitude.  A location
-- must still be visible on the portfolio map.  Until an exact geocode is
-- supplied, use the respective regional centre (never the Ukraine-wide
-- default) as the documented fallback.
UPDATE projects
SET
    latitude = CASE
        WHEN LOWER(region) LIKE '%вінниць%' THEN 49.2331
        WHEN LOWER(region) LIKE '%волин%' THEN 50.7472
        WHEN LOWER(region) LIKE '%дніпропетров%' THEN 48.4647
        WHEN LOWER(region) LIKE '%донець%' THEN 48.0159
        WHEN LOWER(region) LIKE '%житомир%' THEN 50.2547
        WHEN LOWER(region) LIKE '%закарпат%' THEN 48.6208
        WHEN LOWER(region) LIKE '%запоріз%' THEN 47.8388
        WHEN LOWER(region) LIKE '%івано-франків%' THEN 48.9226
        WHEN LOWER(region) LIKE '%київ%' THEN 50.4501
        WHEN LOWER(region) LIKE '%кіровоград%' THEN 48.5079
        WHEN LOWER(region) LIKE '%луган%' THEN 48.5740
        WHEN LOWER(region) LIKE '%львів%' THEN 49.8397
        WHEN LOWER(region) LIKE '%миколаїв%' THEN 46.9750
        WHEN LOWER(region) LIKE '%одес%' THEN 46.4825
        WHEN LOWER(region) LIKE '%полтав%' THEN 49.5883
        WHEN LOWER(region) LIKE '%рівнен%' THEN 50.6199
        WHEN LOWER(region) LIKE '%сум%' THEN 50.9077
        WHEN LOWER(region) LIKE '%терноп%' THEN 49.5535
        WHEN LOWER(region) LIKE '%харків%' THEN 49.9935
        WHEN LOWER(region) LIKE '%херсон%' THEN 46.6354
        WHEN LOWER(region) LIKE '%хмельниць%' THEN 49.4229
        WHEN LOWER(region) LIKE '%черкас%' THEN 49.4444
        WHEN LOWER(region) LIKE '%чернівець%' THEN 48.2921
        WHEN LOWER(region) LIKE '%чернігів%' THEN 51.4982
        ELSE 49.8397
    END,
    longitude = CASE
        WHEN LOWER(region) LIKE '%вінниць%' THEN 28.4682
        WHEN LOWER(region) LIKE '%волин%' THEN 25.3254
        WHEN LOWER(region) LIKE '%дніпропетров%' THEN 35.0462
        WHEN LOWER(region) LIKE '%донець%' THEN 37.8029
        WHEN LOWER(region) LIKE '%житомир%' THEN 28.6587
        WHEN LOWER(region) LIKE '%закарпат%' THEN 22.2879
        WHEN LOWER(region) LIKE '%запоріз%' THEN 35.1396
        WHEN LOWER(region) LIKE '%івано-франків%' THEN 24.7111
        WHEN LOWER(region) LIKE '%київ%' THEN 30.5234
        WHEN LOWER(region) LIKE '%кіровоград%' THEN 32.2623
        WHEN LOWER(region) LIKE '%луган%' THEN 39.3078
        WHEN LOWER(region) LIKE '%львів%' THEN 24.0297
        WHEN LOWER(region) LIKE '%миколаїв%' THEN 31.9946
        WHEN LOWER(region) LIKE '%одес%' THEN 30.7233
        WHEN LOWER(region) LIKE '%полтав%' THEN 34.5514
        WHEN LOWER(region) LIKE '%рівнен%' THEN 26.2516
        WHEN LOWER(region) LIKE '%сум%' THEN 33.4795
        WHEN LOWER(region) LIKE '%терноп%' THEN 25.5948
        WHEN LOWER(region) LIKE '%харків%' THEN 36.2304
        WHEN LOWER(region) LIKE '%херсон%' THEN 32.6169
        WHEN LOWER(region) LIKE '%хмельниць%' THEN 26.9871
        WHEN LOWER(region) LIKE '%черкас%' THEN 32.0598
        WHEN LOWER(region) LIKE '%чернівець%' THEN 25.9358
        WHEN LOWER(region) LIKE '%чернігів%' THEN 31.2893
        ELSE 24.0297
    END
WHERE project_type IN ('subproject', 'subproject_part')
  AND (latitude IS NULL OR longitude IS NULL OR (latitude = 0 AND longitude = 0));
