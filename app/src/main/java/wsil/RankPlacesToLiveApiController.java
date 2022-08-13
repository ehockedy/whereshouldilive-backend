package wsil;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.api.RankPlacesToLiveApi;
import io.swagger.model.PlaceRankSummaries;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;


@RestController
public class RankPlacesToLiveApiController implements RankPlacesToLiveApi {
    private static final Logger log = LoggerFactory.getLogger(RankPlacesToLiveApiController.class);

    public ResponseEntity<PlaceRankSummaries> rankPlacesToLive(
      @Parameter(in = ParameterIn.QUERY, description = "How many items to return at one time (max 100)", schema=@Schema()) @Valid @RequestParam(value = "limit", required = false) Integer limit) {
        log.info("Limit:" + limit);
        return new ResponseEntity<PlaceRankSummaries>(HttpStatus.NOT_IMPLEMENTED);
    }

}
