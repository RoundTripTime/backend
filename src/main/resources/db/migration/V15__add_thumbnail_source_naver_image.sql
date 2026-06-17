ALTER TABLE places
    DROP CONSTRAINT IF EXISTS places_thumbnail_source_check;

ALTER TABLE places
    ADD CONSTRAINT places_thumbnail_source_check
        CHECK (thumbnail_source IN ('NONE','FLICKR','WIKIMEDIA','GOOGLE_PLACES','NAVER_IMAGE'));
