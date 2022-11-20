package wsil;

import java.io.IOException;
import java.util.List;

import com.google.maps.DistanceMatrixApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.TravelMode;

public class GoogleMapsApiHandler {
	private static GeoApiContext context;

	public GoogleMapsApiHandler(String key) {
		context = new GeoApiContext.Builder().apiKey(key).build();
	}

	// Synchronously make Google maps distance matrix API call
	// Since using place IDs, need to append place_id: to each id
	DistanceMatrix mapsMatrixRequest(List<String> placesToTry, List<String> selectedPlaces, TravelMode travelMode) throws ApiException, InterruptedException, IOException {
		return new DistanceMatrixApiRequest(context)
			.origins(mapPlaceIDListToPlaceIDArray(placesToTry))
			.destinations(mapPlaceIDListToPlaceIDArray(selectedPlaces))
			.mode(travelMode)
			.await();
	}

	private String[] mapPlaceIDListToPlaceIDArray(List<String> placeList) {
		return placeList.stream().map((place) -> "place_id:" + place).toList().toArray(new String[placeList.size()]);
	}
}
