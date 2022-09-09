package wsil;

import java.io.IOException;
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

import javax.annotation.PostConstruct;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.google.maps.errors.ApiException;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.DistanceMatrixElement;
import com.google.maps.model.DistanceMatrixElementStatus;
import com.google.maps.model.TravelMode;

import io.swagger.api.RankPlacesToLiveApi;
import io.swagger.api.RankPlacesToLiveStubApi;
import io.swagger.model.ImportantPlace;
import io.swagger.model.PlaceRankSummaries;
import io.swagger.model.PlaceRankSummary;
import io.swagger.model.RankPlacesToLiveBody;
import io.swagger.model.RankPlacesToLiveStubBody;
import io.swagger.model.TravelModesEnum;
import io.swagger.model.JourneySummary;

@RestController
public class RankPlacesToLiveApiController implements RankPlacesToLiveApi, RankPlacesToLiveStubApi {
    private static final Logger logger = LoggerFactory.getLogger(RankPlacesToLiveApiController.class);

    @Value("${api.key}")
    private String apiKey;
    private GoogleMapsApiHandler googleMapsApiHandler;

    @PostConstruct
    public void setup() {
        this.googleMapsApiHandler = new GoogleMapsApiHandler(this.apiKey);
    }

    @Override
    public ResponseEntity<PlaceRankSummaries> rankPlacesToLive(@Valid RankPlacesToLiveBody body) {
        // Extract a list of just the ids and ttpms
        List<ImportantPlace> importantPlaces = body.getImportantPlaces();
        List<String> importantPlaceIds = importantPlaces.stream().map(place -> place.getId()).toList();

        // Construct list of possible travel mode types
        List<TravelMode> travelModes = body.getTravelModes() == null
            ? Arrays.asList(TravelMode.DRIVING)  // Nothing specified, use Google maps default
            : body.getTravelModes().stream().map(mode -> swaggerTravelModeToGoogleTravelMode(mode)).toList();

        // Make distance matrix requests, one for each travel mode type
        Map<TravelMode, DistanceMatrix> mapMatrixResponses = new HashMap<>();
        try {
            for (TravelMode mode: travelModes) {
                mapMatrixResponses.put(mode, this.googleMapsApiHandler.mapsMatrixRequest(
                    body.getPlacesToLive(),
                    importantPlaceIds,
                    mode)
                );
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
        ListIterator<String> it = body.getPlacesToLive().listIterator();
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
                new PlaceRankSummary()
                    .name(placeToLiveName)
                    .success(ttpm.isPresent())
                    .totalTravelTimePerMonth(ttpm.orElse(0f))
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
            if (importantPlaceResult.isSuccess()) {
                total += importantPlaceResult.getTravelTime() * importantPlace.getVisitsPerMonth();
            } else {
                // Distance calculation could not be calculated for one of the places, so evaluation
                // of this place to live is invalid.
                return Optional.empty();
            }
        }
        return Optional.of(total);
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
                JourneySummary result = new JourneySummary()
                    .travelTime(ttpm)
                    .travelMode(googleTravelModeToSwaggerTravelMode(travelMode))
                    .success(distanceResult.status.equals(DistanceMatrixElementStatus.OK) || isSamePlace)
                    .name(importantPlaceName);

                // Decide if the result between the current place to live, and this important place
                // is better than the current best result.
                if (!journeySummaries.containsKey(importantPlaceName) || !journeySummaries.get(importantPlaceName).isSuccess()) {
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

    @Override
    public ResponseEntity<PlaceRankSummaries> rankPlacesToLive(@Valid RankPlacesToLiveStubBody body) {
        // To save having to make API call each time (could geet chanrged), just provide some mock data
        PlaceRankSummaries placeRankSummaries = new PlaceRankSummaries();
        placeRankSummaries.add(
            new PlaceRankSummary()
                .name("Testville")
                .success(true)
                .totalTravelTimePerMonth(2500f)
                .fastestJourneys(new ArrayList<>() {
                    {
                        add(new JourneySummary()
                            .name("Homeington")
                            .success(true)
                            .travelMode(TravelModesEnum.DRIVING)
                            .travelTime(2000f));
                        add(new JourneySummary()
                            .name("Housely")
                            .success(true)
                            .travelMode(TravelModesEnum.PUBLIC_TRANSPORT)
                            .travelTime(500f));
                    }
                })
        );
        placeRankSummaries.add(
            new PlaceRankSummary()
                .name("Stubhampton")
                .success(true)
                .totalTravelTimePerMonth(2750f)
                .fastestJourneys(new ArrayList<>() {
                    {
                        add(new JourneySummary()
                            .name("Homeington")
                            .success(true)
                            .travelMode(TravelModesEnum.DRIVING)
                            .travelTime(1250f));
                        add(new JourneySummary()
                            .name("Housely")
                            .success(true)
                            .travelMode(TravelModesEnum.PUBLIC_TRANSPORT)
                            .travelTime(1500f));
                    }
                })
        );
        return new ResponseEntity<PlaceRankSummaries>(placeRankSummaries, HttpStatus.OK);
    }

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
