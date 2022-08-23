package wsil;

import java.io.IOException;
import java.util.List;

import com.google.maps.DistanceMatrixApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DistanceMatrix;

public class GoogleMapsApiHandler {
	private static GeoApiContext context;

	public GoogleMapsApiHandler(String key) {
		context = new GeoApiContext.Builder().apiKey(key).build();
	}

	// Synchronously make Google maps distance matrix API call
	DistanceMatrix mapsMatrixRequest(List<String> placesToTry, List<String> selectedPlaces) throws ApiException, InterruptedException, IOException {
		return new DistanceMatrixApiRequest(context)
			.origins(placesToTry.toArray(new String[placesToTry.size()]))
			.destinations(selectedPlaces.toArray(new String[selectedPlaces.size()]))
			.await();
	}
}
