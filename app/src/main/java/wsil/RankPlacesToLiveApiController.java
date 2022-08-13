package wsil;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.api.RankPlacesToLiveApi;
import io.swagger.model.PlaceRankSummaries;

@RestController
public class RankPlacesToLiveApiController implements RankPlacesToLiveApi {
    private static final Logger log = LoggerFactory.getLogger(RankPlacesToLiveApiController.class);

    @Override
    public ResponseEntity<PlaceRankSummaries> rankPlacesToLive(
        @NotNull @Valid List<String> placesToLive,
        @NotNull @Valid List<String> importantPlaces) {
            log.info("ptl: " + placesToLive.size());
            log.info("ip: " + importantPlaces.size());
        return new ResponseEntity<PlaceRankSummaries>(HttpStatus.NOT_IMPLEMENTED);
    }
}
