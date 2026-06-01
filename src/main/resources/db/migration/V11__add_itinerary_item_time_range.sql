-- itinerary_items에 시작/종료 시간 컬럼 추가
ALTER TABLE itinerary_items
    ADD COLUMN start_time TIME,
    ADD COLUMN end_time   TIME;
