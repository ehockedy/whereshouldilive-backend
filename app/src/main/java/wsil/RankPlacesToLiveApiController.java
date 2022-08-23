package wsil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

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

@RestController
public class RankPlacesToLiveApiController implements RankPlacesToLiveApi {
    private static final Logger log = LoggerFactory.getLogger(RankPlacesToLiveApiController.class);

    @Value("${api.key}")
    private String apiKey;
    private GoogleMapsApiHandler googleMapsApiHandler;

    @PostConstruct
    public void setup() {
        this.googleMapsApiHandler = new GoogleMapsApiHandler(this.apiKey);
    }

    @Override
    public ResponseEntity<PlaceRankSummaries> rankPlacesToLive(
        @NotNull @Valid List<String> placesToLive,
        @NotNull @Valid List<String> importantPlaces) {

        // Make distance matrix request
        DistanceMatrix mapMatrixResponse;
        try {
            mapMatrixResponse = this.googleMapsApiHandler.mapsMatrixRequest(placesToLive, importantPlaces);
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
        List<Long> travelTimesPerMonth = new ArrayList<>();  // TODO long implies once per month is least frequent
        for (int i = 0; i < importantPlaces.size(); i++) {
            travelTimesPerMonth.add(1L);
        }
        
        ListIterator<String> it = placesToLive.listIterator();
        while (it.hasNext()) {
            int idx = it.nextIndex();
            String placeName = it.next();
            Optional<Long> ttpm = calculateTravelTimePerMonth(mapMatrixResponse.rows[idx], travelTimesPerMonth);
            placeRankSummaries.add(
                new PlaceRankSummary()
                    .name(placeName)
                    .success(ttpm.isPresent())
                    .totalTravelTimePerMonth(ttpm.orElse(0L))
            );
        }

        // Sort results from shortest to longest travel time per month
        placeRankSummaries.sort(Comparator.comparingLong(PlaceRankSummary::getTotalTravelTimePerMonth));

        return new ResponseEntity<PlaceRankSummaries>(placeRankSummaries, HttpStatus.OK);
    }

    Optional<Long> calculateTravelTimePerMonth(DistanceMatrixRow row, List<Long> travelTimesPerMonth) {
        Long total = 0L;
        int idx = 0;
        for (DistanceMatrixElement element : row.elements) {
            if (element.status == DistanceMatrixElementStatus.OK) {
                total += element.duration.inSeconds * travelTimesPerMonth.get(idx);
            } else {
                // Distance calculation could not be caluclated for one of the places, so evaluation
                // of this place to live is invalid.
                return Optional.empty();
            }
        }
        return Optional.of(total);
    }
}
