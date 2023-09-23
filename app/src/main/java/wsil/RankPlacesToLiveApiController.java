package wsil;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TimeZone;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import com.google.maps.errors.ApiException;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.DistanceMatrixElement;
import com.google.maps.model.DistanceMatrixElementStatus;
import com.google.maps.model.DistanceMatrixRow;
import com.google.maps.model.TravelMode;

import openapi.gen.api.RankPlacesToLiveApi;
import openapi.gen.model.ImportantPlace;
import openapi.gen.model.PlaceRankSummaries;
import openapi.gen.model.PlaceRankSummary;
import openapi.gen.model.RankPlacesToLiveRequest;
import openapi.gen.model.TravelModesEnum;
import openapi.gen.model.JourneySummary;
import openapi.gen.model.LatLng;

@RestController
public class RankPlacesToLiveApiController implements RankPlacesToLiveApi {
    private static final Logger logger = LoggerFactory.getLogger(RankPlacesToLiveApiController.class);

    private GoogleMapsApiHandler googleMapsApiHandler;

    @PostConstruct
    public void setup() {
        // Read api key from environment variable
        String apiKey = System.getenv("GOOGLE_MAPS_API_KEY");
        this.googleMapsApiHandler = new GoogleMapsApiHandler(apiKey);
    }

    // @Override
    @CrossOrigin(origins = "*")
    public ResponseEntity<PlaceRankSummaries> rankPlacesToLive(@Valid RankPlacesToLiveRequest body) {
        // Extract a list of just the ids and ttpms
        List<String> placesToLive = body.getPlacesToLive();
        List<ImportantPlace> importantPlaces = body.getImportantPlaces();
        List<String> importantPlaceIds = importantPlaces.stream().map(place -> place.getId()).toList();
        LatLng latLng = body.getLatLng();
        if (importantPlaceIds.isEmpty() || placesToLive.isEmpty() || latLng == null) {
            return new ResponseEntity<PlaceRankSummaries>(HttpStatus.BAD_REQUEST);
        }

        // Construct list of possible travel mode types
        List<TravelMode> travelModes = body.getTravelModes() == null
            ? Arrays.asList(TravelMode.DRIVING)  // Nothing specified, use Google maps default
            : body.getTravelModes().stream().map(mode -> swaggerTravelModeToGoogleTravelMode(mode)).toList();

        // Make distance matrix requests, one for each travel mode type
        Map<TravelMode, DistanceMatrix> mapMatrixResponses = new HashMap<>();
        try {
            TimeZone timezone = this.googleMapsApiHandler.mapsTimezoneRequest(latLng);
            for (TravelMode mode: travelModes) {
                DistanceMatrix distanceMatrix = makeDistanceMatrixRequests(placesToLive, importantPlaceIds, mode, timezone);
                mapMatrixResponses.put(mode, distanceMatrix);
            }
        } catch (ApiException e) {
            e.printStackTrace();
            return new ResponseEntity<PlaceRankSummaries>(HttpStatus.BAD_REQUEST);
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            return new ResponseEntity<PlaceRankSummaries>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Process results
        // Every row corresponds to a potential place to live. For each one, calulate the total time travelling
        // per month. Then rank the places to live.
        PlaceRankSummaries placeRankSummaries = new PlaceRankSummaries();
        ListIterator<String> it = placesToLive.listIterator();
        while (it.hasNext()) {
            int placeToLiveIdx = it.nextIndex();
            String placeToLiveName = it.next();

            // We need to get the shortest travel times between the currently considered place to live and all the important
            // places across all modes of transport.
            Map<String, JourneySummary> journeySummaries = aggregateDistanceMatrices(mapMatrixResponses, importantPlaceIds, placeToLiveIdx, placeToLiveName);

            // Calculate travel time between current place to live and all imporant places
            Optional<Float> ttpm = calculateTravelTimePerMonth(journeySummaries, importantPlaces);

            // Add to results
            placeRankSummaries.add(
                new PlaceRankSummary(placeToLiveName, ttpm.isPresent(), ttpm.orElse(0f))
                    .fastestJourneys(new ArrayList<>(journeySummaries.values()))
            );
        }

        // Sort results from shortest to longest travel time per month
        placeRankSummaries.sort(Comparator.comparingDouble(PlaceRankSummary::getTotalTravelTimePerMonth));

        return new ResponseEntity<PlaceRankSummaries>(placeRankSummaries, HttpStatus.OK);
    }

    Optional<Float> calculateTravelTimePerMonth(Map<String, JourneySummary> journeySummaries, List<ImportantPlace> importantPlaces) {
        Float total = 0f;
        for (ImportantPlace importantPlace : importantPlaces) {
            if (!journeySummaries.containsKey(importantPlace.getId())) {
                return Optional.empty();
            }
            JourneySummary importantPlaceResult = journeySummaries.get(importantPlace.getId());
            if (importantPlaceResult.getSuccess()) {
                total += importantPlaceResult.getTravelTime() * importantPlace.getVisitsPerMonth();
            } else {
                // Distance calculation could not be calculated for one of the places, so evaluation
                // of this place to live is invalid.
                return Optional.empty();
            }
        }
        return Optional.of(total);
    }

    Instant createDepartureTimeInstant(int hour, int minute, DayOfWeek dayOfWeek, TimeZone timezone) {
        return LocalTime
            .of(hour, minute)
            .atDate(LocalDate.now().with(TemporalAdjusters.next(dayOfWeek)))
            .atZone(timezone.toZoneId())
            .toInstant();
    }

    /**
     * Make the distance matrix request, and if applicable make more to get best coverage over
     * different times of day and days of week. This meaks it's not tied to user's current time,
     * or closures or available departure time.
     */
    DistanceMatrix makeDistanceMatrixRequests(
        List<String> placesToLive,
        List<String> importantPlaceIds,
        TravelMode travelMode,
        TimeZone timezone
    ) throws ApiException, InterruptedException, IOException {
        // Use (local) noon next monday to ensure a consistent time to evaluate all journeys from.
        Instant noonNextMonday = createDepartureTimeInstant(12, 0, DayOfWeek.MONDAY, timezone);
        DistanceMatrix baseDistanceMatrix = this.googleMapsApiHandler.mapsMatrixRequest(
            placesToLive,
            importantPlaceIds,
            travelMode,
            noonNextMonday
        );

        // Only need to try different times of travel if on public transport, since it can vary so much
        if (travelMode.equals(TravelMode.TRANSIT) && baseDistanceMatrix != null && baseDistanceMatrix.rows != null) {
            // Fairly arbitrary list of departure times, but covers most of the day and week
            List<Instant> departureInstants = new ArrayList<Instant>(List.of(
                createDepartureTimeInstant(7, 0, DayOfWeek.MONDAY, timezone),
                createDepartureTimeInstant(9, 30, DayOfWeek.TUESDAY, timezone),
                createDepartureTimeInstant(16, 0, DayOfWeek.SATURDAY, timezone),
                createDepartureTimeInstant(20, 30, DayOfWeek.SUNDAY, timezone)
            ));
            // Iterate over the departure times and replace the current travel time if faster
            for (int timeIdx = 0; timeIdx < departureInstants.size(); timeIdx++) {
                Instant departureInstant = departureInstants.get(timeIdx);
                DistanceMatrix distanceMatrix = this.googleMapsApiHandler.mapsMatrixRequest(
                    placesToLive,
                    importantPlaceIds,
                    travelMode,
                    departureInstant
                );
                for (int rowIdx = 0; rowIdx < baseDistanceMatrix.rows.length; rowIdx++) {
                    final DistanceMatrixRow row = baseDistanceMatrix.rows[rowIdx];
                    for (int colIdx = 0; colIdx < row.elements.length; colIdx++) {
                        final DistanceMatrixElement baseElement = row.elements[colIdx];
                        final DistanceMatrixElement comparisonElement = distanceMatrix.rows[rowIdx].elements[colIdx];
                        if (comparisonElement.status == DistanceMatrixElementStatus.OK) {
                            if (baseElement.status != DistanceMatrixElementStatus.OK ||
                                baseElement.duration.inSeconds > comparisonElement.duration.inSeconds) {
                                baseDistanceMatrix.rows[rowIdx].elements[colIdx] = comparisonElement;
                            }
                        }
                    }
                }
            }
        }
        return baseDistanceMatrix;
    }

    /**
     * Since each travel type has to have a separate distance matrix, we need to aggregate those results into
     * a single result that has the fastest journey time between each place to live and each important place
     * across each mode of transport. This function aggregates the results and returns a map where each key is
     * an important place, and the value is the summary of the journey with the shortest duration.
     * @param matrixMapResponses Distance matrix result for each travel type
     * @param importantPlaceIds Ordered list of important place ids
     * @param placeToLiveIdx Index of current place to live that is being evaluated
     * @return Map of shortest journeys to each important palce from the current place to live
     */
    Map<String, JourneySummary> aggregateDistanceMatrices(
        Map<TravelMode, DistanceMatrix> matrixMapResponses,
        List<String> importantPlaceIds,
        int placeToLiveIdx,
        String placeToLiveName
    ) {
        Iterator<Entry<TravelMode, DistanceMatrix>> matrixIterator = matrixMapResponses.entrySet().iterator();
        Map<String, JourneySummary> journeySummaries = new HashMap<>(); // Map that stores the best travel results i.e. with shortest duration for each important place
        while (matrixIterator.hasNext()) {
            Entry<TravelMode, DistanceMatrix> entry = matrixIterator.next();
            TravelMode travelMode = entry.getKey();
            DistanceMatrix distanceMatrix = entry.getValue();
            DistanceMatrixElement[] distanceResultsToImportantPlaces = distanceMatrix.rows[placeToLiveIdx].elements;

            // Iterate over the important places
            int importantPlaceIdx = 0;
            for (DistanceMatrixElement distanceResult: distanceResultsToImportantPlaces) {
                String importantPlaceName = importantPlaceIds.get(importantPlaceIdx);

                // Maps API returns error if origin and destination are the same, but for this service it results
                // in a travel time of 0 (very good), we want to keep it.
                Boolean isSamePlace = importantPlaceName.equals(placeToLiveName);
                Float ttpm = isSamePlace || !distanceResult.status.equals(DistanceMatrixElementStatus.OK)
                    ? 0F
                    : Float.valueOf(distanceResult.duration.inSeconds);
                JourneySummary result = new JourneySummary(
                    importantPlaceName,
                    distanceResult.status.equals(DistanceMatrixElementStatus.OK) || isSamePlace,
                    googleTravelModeToSwaggerTravelMode(travelMode),
                    ttpm
                );

                // Decide if the result between the current place to live, and this important place
                // is better than the current best result.
                if (!journeySummaries.containsKey(importantPlaceName) || !journeySummaries.get(importantPlaceName).getSuccess()) {
                    // No entry for this important place, or current entry was not a success so add to results map
                    // Does not matter is new entry is also not success, this is handled later.
                    journeySummaries.put(importantPlaceName, result);
                } else if (distanceResult.status == DistanceMatrixElementStatus.OK &&
                    ttpm.compareTo(journeySummaries.get(importantPlaceName).getTravelTime()) < 0) {
                    // Shorter time to travel, so is best
                    journeySummaries.put(importantPlaceName, result);
                }
                importantPlaceIdx+=1;
            }
        }
        return journeySummaries;
    }

    // @Override
    // public ResponseEntity<PlaceRankSummaries> rankPlacesToLive(@Valid RankPlacesToLiveRequest body) {
    //     // To save having to make API call each time (could geet chanrged), just provide some mock data
    //     PlaceRankSummaries placeRankSummaries = new PlaceRankSummaries();
    //     placeRankSummaries.add(
    //         new PlaceRankSummary()
    //             .name("Testville")
    //             .success(true)
    //             .totalTravelTimePerMonth(2500f)
    //             .fastestJourneys(new ArrayList<>() {
    //                 {
    //                     add(new JourneySummary()
    //                         .name("Homeington")
    //                         .success(true)
    //                         .travelMode(TravelModesEnum.DRIVING)
    //                         .travelTime(2000f));
    //                     add(new JourneySummary()
    //                         .name("Housely")
    //                         .success(true)
    //                         .travelMode(TravelModesEnum.PUBLIC_TRANSPORT)
    //                         .travelTime(500f));
    //                 }
    //             })
    //     );
    //     placeRankSummaries.add(
    //         new PlaceRankSummary()
    //             .name("Stubhampton")
    //             .success(true)
    //             .totalTravelTimePerMonth(2750f)
    //             .fastestJourneys(new ArrayList<>() {
    //                 {
    //                     add(new JourneySummary()
    //                         .name("Homeington")
    //                         .success(true)
    //                         .travelMode(TravelModesEnum.DRIVING)
    //                         .travelTime(1250f));
    //                     add(new JourneySummary()
    //                         .name("Housely")
    //                         .success(true)
    //                         .travelMode(TravelModesEnum.PUBLIC_TRANSPORT)
    //                         .travelTime(1500f));
    //                 }
    //             })
    //     );
    //     return new ResponseEntity<PlaceRankSummaries>(placeRankSummaries, HttpStatus.OK);
    // }

    // TODO move to helper class
    // Converts to this API enum to Google Maps enum value
    TravelMode swaggerTravelModeToGoogleTravelMode(TravelModesEnum mode) {
        switch(mode) {
            case DRIVING:
                return TravelMode.DRIVING;
            case CYCLING:
                return TravelMode.BICYCLING;
            case PUBLIC_TRANSPORT:
                return TravelMode.TRANSIT;
            case WALKING:
                return TravelMode.WALKING;
            default:
                return TravelMode.DRIVING;
        }
    }

    TravelModesEnum googleTravelModeToSwaggerTravelMode(TravelMode mode) {
        switch(mode) {
            case DRIVING:
                return TravelModesEnum.DRIVING;
            case BICYCLING:
                return TravelModesEnum.CYCLING;
            case TRANSIT:
                return TravelModesEnum.PUBLIC_TRANSPORT;
            case WALKING:
                return TravelModesEnum.WALKING;
            default:
                return TravelModesEnum.DRIVING;
        }
    }
}
