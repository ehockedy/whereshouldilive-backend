package wsil;

import java.io.IOException;
import java.time.Instant;

import java.util.List;
import java.util.TimeZone;

import com.google.maps.TimeZoneApi;
import com.google.maps.DistanceMatrixApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.TravelMode;

import io.swagger.model.LatLng;

public class GoogleMapsApiHandler {
	private static GeoApiContext context;

	public GoogleMapsApiHandler(String key) {
		context = new GeoApiContext.Builder().apiKey(key).build();
	}

	// Synchronously make Google maps distance matrix API call
	// Since using place IDs, need to append place_id: to each id
	DistanceMatrix mapsMatrixRequest(List<String> placesToTry, List<String> selectedPlaces, TravelMode travelMode, Instant departureTime) throws ApiException, InterruptedException, IOException {
		return new DistanceMatrixApiRequest(context)
			.departureTime(departureTime)
			.origins(mapPlaceIDListToPlaceIDArray(placesToTry))
			.destinations(mapPlaceIDListToPlaceIDArray(selectedPlaces))
			.mode(travelMode)
			.await();
	}

	TimeZone mapsTimezoneRequest(LatLng latLng) throws ApiException, InterruptedException, IOException {
		return TimeZoneApi.getTimeZone(
			context,
			new com.google.maps.model.LatLng(latLng.getLat(), latLng.getLng())
		).await();
	}

	private String[] mapPlaceIDListToPlaceIDArray(List<String> placeList) {
		return placeList.stream().map((place) -> "place_id:" + place).toList().toArray(new String[placeList.size()]);
	}
}
