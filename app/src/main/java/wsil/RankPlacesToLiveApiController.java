package wsil;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
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
import com.google.maps.model.DistanceMatrixRow;

import io.swagger.api.RankPlacesToLiveApi;
import io.swagger.model.PlaceRankSummaries;
import io.swagger.model.PlaceRankSummary;
import io.swagger.model.RankPlacesToLiveBody;

@RestController
public class RankPlacesToLiveApiController implements RankPlacesToLiveApi {
    private static final Logger logger = LoggerFactory.getLogger(RankPlacesToLiveApiController.class);

    @Value("${api.key}")
    private String apiKey;
    private GoogleMapsApiHandler googleMapsApiHandler;

    @PostConstruct
    public void setup() {
        this.googleMapsApiHandler = new GoogleMapsApiHandler(this.apiKey);
    }

    @Override
    public ResponseEntity<PlaceRankSummaries> rankPlacesToLivePost(@Valid RankPlacesToLiveBody body) {
        // Extract a list of just the ids and ttpms
        List<String> importantPlaceIds = body.getImportantPlaces().stream().map(place -> place.getId()).toList();
        List<Float> travelTimesPerMonth = body.getImportantPlaces().stream().map(place -> place.getVisitsPerMonth()).toList();

        // Make distance matrix request
        DistanceMatrix mapMatrixResponse;
        try {
            mapMatrixResponse = this.googleMapsApiHandler.mapsMatrixRequest(body.getPlacesToLive(), importantPlaceIds);
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
            int idx = it.nextIndex();
            String placeName = it.next();
            Optional<Float> ttpm = calculateTravelTimePerMonth(mapMatrixResponse.rows[idx], travelTimesPerMonth);
            placeRankSummaries.add(
                new PlaceRankSummary()
                    .name(placeName)
                    .success(ttpm.isPresent())
                    .totalTravelTimePerMonth(ttpm.orElse(0f))
            );
        }

        // Sort results from shortest to longest travel time per month
        placeRankSummaries.sort(Comparator.comparingDouble(PlaceRankSummary::getTotalTravelTimePerMonth));

        return new ResponseEntity<PlaceRankSummaries>(placeRankSummaries, HttpStatus.OK);
    }

    Optional<Float> calculateTravelTimePerMonth(DistanceMatrixRow row, List<Float> travelTimesPerMonth) {
        Float total = 0f;
        int idx = 0;
        for (DistanceMatrixElement element : row.elements) {
            if (element.status == DistanceMatrixElementStatus.OK) {
                total += Float.valueOf(element.duration.inSeconds) * travelTimesPerMonth.get(idx);
            } else {
                // Distance calculation could not be caluclated for one of the places, so evaluation
                // of this place to live is invalid.
                return Optional.empty();
            }
            idx += 1;
        }
        return Optional.of(total);
    }
}
