package roundtrip.place.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.candidate.domain.entity.PlaceCandidate;
import roundtrip.candidate.domain.repository.PlaceCandidateRepository;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.extract.domain.entity.ExtractionJob;
import roundtrip.extract.domain.repository.ExtractionJobRepository;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.entity.PlaceCategory;
import roundtrip.place.domain.entity.PlaceReview;
import roundtrip.place.domain.repository.PlaceRepository;
import roundtrip.sourcelink.domain.entity.SourceLink;
import roundtrip.sourcelink.domain.repository.SourceLinkRepository;
import roundtrip.sourcelink.infrastructure.external.KakaoLocalClient;
import roundtrip.sourcelink.infrastructure.external.KakaoLocalDocument;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceCandidateRepository candidateRepository;
    private final ExtractionJobRepository jobRepository;
    private final SourceLinkRepository sourceLinkRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final ThumbnailFetcher thumbnailFetcher;

    @Transactional(readOnly = true)
    public PlaceDetailResult getPlace(UUID placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        SourceLink sourceLink = findSourceLinkForPlace(placeId);
        return new PlaceDetailResult(place, sourceLink);
    }

    @Transactional
    public List<Place> searchPlaces(String query, String provider) {
        List<KakaoLocalDocument> docs = kakaoLocalClient.searchByKeyword(query);
        return docs.stream()
                .map(doc -> findOrCreateFromKakao(doc))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlaceRepository.PlaceSimilarRow> getSimilarPlaces(UUID placeId, int limit) {
        placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        return placeRepository.findSimilarPlacesRanked(placeId, limit);
    }

    @Transactional(readOnly = true)
    public List<PlaceRepository.DiscoverRow> getDiscover(
            UUID userId, int limit, String categoryStr, String countryCode) {

        PlaceCategory category = parseCategory(categoryStr);
        List<PlaceRepository.DiscoverRow> personalized =
                placeRepository.findDiscoverPlaces(userId, limit, category, countryCode);

        if (personalized.size() >= limit) {
            return personalized;
        }

        int remaining = limit - personalized.size();
        List<UUID> alreadyIncluded = personalized.stream()
                .map(PlaceRepository.DiscoverRow::id)
                .toList();
        List<PlaceRepository.DiscoverRow> coldstart =
                placeRepository.findDiscoverPlacesColdStart(userId, remaining, category, countryCode, alreadyIncluded);

        List<PlaceRepository.DiscoverRow> merged = new java.util.ArrayList<>(personalized);
        merged.addAll(coldstart);
        return merged;
    }

    @Transactional(readOnly = true)
    public List<SourceLinkResult> getSourceLinks(UUID placeId) {
        placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        List<PlaceCandidate> candidates = candidateRepository.findByPlaceId(placeId);
        return candidates.stream()
                .map(c -> {
                    Optional<ExtractionJob> job = jobRepository.findById(c.getJobId());
                    if (job.isEmpty()) return null;
                    Optional<SourceLink> sl = sourceLinkRepository.findById(job.get().getSourceLinkId());
                    return sl.map(s -> new SourceLinkResult(s, c)).orElse(null);
                })
                .filter(r -> r != null)
                .distinct()
                .toList();
    }

    private SourceLink findSourceLinkForPlace(UUID placeId) {
        return candidateRepository.findFirstByPlaceId(placeId)
                .flatMap(c -> jobRepository.findById(c.getJobId()))
                .flatMap(j -> sourceLinkRepository.findById(j.getSourceLinkId()))
                .orElse(null);
    }

    private Place findOrCreateFromKakao(KakaoLocalDocument doc) {
        return placeRepository.findByKakaoPlaceId(doc.id())
                .orElseGet(() -> {
                    Place place = Place.create(
                            doc.placeName(),
                            doc.y() != null ? new BigDecimal(doc.y()) : null,
                            doc.x() != null ? new BigDecimal(doc.x()) : null,
                            mapKakaoCategory(doc.categoryGroupCode()),
                            "KR",
                            doc.id(),
                            null
                    );
                    Place saved = placeRepository.save(place);
                    thumbnailFetcher.fetchAndUpdate(saved.getId());
                    return saved;
                });
    }

    private PlaceCategory mapKakaoCategory(String code) {
        if (code == null) return PlaceCategory.ETC;
        return switch (code) {
            case "FD6" -> PlaceCategory.RESTAURANT;
            case "CE7" -> PlaceCategory.CAFE;
            case "AD5" -> PlaceCategory.ACCOMMODATION;
            case "AT4" -> PlaceCategory.ATTRACTION;
            case "MT1", "CS2" -> PlaceCategory.ETC;
            default -> PlaceCategory.ETC;
        };
    }

    private PlaceCategory parseCategory(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return PlaceCategory.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Reviews ──

    @Transactional(readOnly = true)
    public ReviewListResult getReviews(UUID placeId, UUID cursorId, int limit) {
        placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        List<PlaceReview> reviews = placeRepository.findReviewsByPlaceIdBefore(placeId, cursorId, limit + 1);
        boolean hasMore = reviews.size() > limit;
        if (hasMore) {
            reviews = reviews.subList(0, limit);
        }
        UUID nextCursor = hasMore && !reviews.isEmpty() ? reviews.getLast().getId() : null;
        return new ReviewListResult(reviews, nextCursor);
    }

    @Transactional
    public PlaceReview createReview(UUID placeId, UUID userId, short rating, String body) {
        placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        if (placeRepository.existsReviewByPlaceIdAndUserId(placeId, userId)) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        PlaceReview review = PlaceReview.create(placeId, userId, rating, body);
        return placeRepository.saveReview(review);
    }

    @Transactional
    public void deleteReview(UUID placeId, UUID reviewId, UUID userId) {
        PlaceReview review = placeRepository.findReviewByIdAndPlaceId(reviewId, placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "타인의 리뷰를 삭제할 수 없습니다.");
        }
        placeRepository.deleteReview(review);
    }

    public record PlaceDetailResult(Place place, SourceLink sourceLink) {}

    public record SourceLinkResult(SourceLink sourceLink, PlaceCandidate candidate) {}

    public record ReviewListResult(List<PlaceReview> reviews, UUID nextCursor) {}
}
