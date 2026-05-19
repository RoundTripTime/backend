package roundtrip.place.infrastructure.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.entity.PlaceCategory;
import roundtrip.place.domain.repository.PlaceRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlaceRepositoryImpl implements PlaceRepository {

    private final PlaceJpaRepository jpa;

    @PersistenceContext
    private EntityManager em;

    @Override
    public Place save(Place place) {
        return jpa.save(place);
    }

    @Override
    public Optional<Place> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Place> findByKakaoPlaceId(String kakaoPlaceId) {
        return jpa.findByKakaoPlaceId(kakaoPlaceId);
    }

    @Override
    public List<Place> findSimilarPlaces(UUID placeId, int limit) {
        return findSimilarPlacesRanked(placeId, limit).stream()
                .map(row -> jpa.findById(row.id()).orElseThrow())
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PlaceSimilarRow> findSimilarPlacesRanked(UUID placeId, int limit) {
        String sql = """
                SELECT p.id::text, p.canonical_name, p.category, p.latitude, p.longitude,
                       p.thumbnail_url, p.thumbnail_source,
                       1 - (p.embedding <=> ref.embedding) AS similarity_score
                FROM places p
                CROSS JOIN (SELECT embedding FROM places WHERE id = :placeId) ref
                WHERE p.id != :placeId
                  AND p.embedding IS NOT NULL
                  AND ref.embedding IS NOT NULL
                ORDER BY p.embedding <=> ref.embedding
                LIMIT :limit
                """;

        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("placeId", placeId)
                .setParameter("limit", limit)
                .getResultList();

        return rows.stream().map(r -> new PlaceSimilarRow(
                UUID.fromString((String) r[0]),
                (String) r[1],
                parseCategory((String) r[2]),
                (BigDecimal) r[3],
                (BigDecimal) r[4],
                (String) r[5],
                (String) r[6],
                r[7] != null ? ((Number) r[7]).doubleValue() : 0.0
        )).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DiscoverRow> findDiscoverPlaces(UUID userId, int limit, PlaceCategory category, String countryCode) {
        String sql = """
                WITH user_avg AS (
                    SELECT avg(p.embedding) AS avg_vec
                    FROM collection_places cp
                    JOIN collections c ON cp.collection_id = c.id
                    JOIN places p ON cp.place_id = p.id
                    WHERE c.user_id = :userId AND p.embedding IS NOT NULL
                ),
                excluded AS (
                    SELECT cp.place_id
                    FROM collection_places cp
                    JOIN collections c ON cp.collection_id = c.id
                    WHERE c.user_id = :userId
                )
                SELECT p.id::text, p.canonical_name, p.category, p.latitude, p.longitude,
                       p.country_code, p.thumbnail_url, p.thumbnail_source,
                       1 - (p.embedding <=> ua.avg_vec) AS similarity_score
                FROM places p, user_avg ua
                WHERE p.id NOT IN (SELECT place_id FROM excluded)
                  AND p.embedding IS NOT NULL
                  AND ua.avg_vec IS NOT NULL
                  AND (CAST(:category AS VARCHAR) IS NULL OR p.category = CAST(:category AS VARCHAR))
                  AND (CAST(:countryCode AS VARCHAR) IS NULL OR p.country_code = CAST(:countryCode AS VARCHAR))
                ORDER BY p.embedding <=> ua.avg_vec
                LIMIT :limit
                """;

        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("limit", limit)
                .setParameter("category", category != null ? category.name() : null)
                .setParameter("countryCode", countryCode)
                .getResultList();

        return toDiscoverRows(rows);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DiscoverRow> findDiscoverPlacesColdStart(UUID userId, int limit, PlaceCategory category, String countryCode) {
        String sql = """
                WITH excluded AS (
                    SELECT cp.place_id
                    FROM collection_places cp
                    JOIN collections c ON cp.collection_id = c.id
                    WHERE c.user_id = :userId
                )
                SELECT p.id::text, p.canonical_name, p.category, p.latitude, p.longitude,
                       p.country_code, p.thumbnail_url, p.thumbnail_source,
                       0.0 AS similarity_score
                FROM places p
                WHERE p.id NOT IN (SELECT place_id FROM excluded)
                  AND (CAST(:category AS VARCHAR) IS NULL OR p.category = CAST(:category AS VARCHAR))
                  AND (CAST(:countryCode AS VARCHAR) IS NULL OR p.country_code = CAST(:countryCode AS VARCHAR))
                ORDER BY p.created_at DESC
                LIMIT :limit
                """;

        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("limit", limit)
                .setParameter("category", category != null ? category.name() : null)
                .setParameter("countryCode", countryCode)
                .getResultList();

        return toDiscoverRows(rows);
    }

    private List<DiscoverRow> toDiscoverRows(List<Object[]> rows) {
        return rows.stream().map(r -> new DiscoverRow(
                UUID.fromString((String) r[0]),
                (String) r[1],
                parseCategory((String) r[2]),
                (BigDecimal) r[3],
                (BigDecimal) r[4],
                (String) r[5],
                (String) r[6],
                (String) r[7],
                r[8] != null ? ((Number) r[8]).doubleValue() : 0.0
        )).toList();
    }

    private PlaceCategory parseCategory(String value) {
        if (value == null) return null;
        try {
            return PlaceCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
