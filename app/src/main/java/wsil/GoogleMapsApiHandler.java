package wsil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class GoogleMapsApiHandler {
	private static String apiKey;
	private static HttpRequest.Builder httpRequestBuilder;
	private static String googleMapsURL = "https://maps.googleapis.com/maps/api/";
	private static Logger logger = Logger.getLogger(GoogleMapsApiHandler.class.getName());

	public GoogleMapsApiHandler(String key) {
		apiKey = key;
		httpRequestBuilder = HttpRequest.newBuilder();
	}

	String getPlaceIdFromAddress(String address) throws IOException, InterruptedException {
		// Make a request to google geocode API to get all the info on the address
		// The address should be enough to get information
		String sanitizedAddress = address.replace(" ", "-");
		HttpRequest request = httpRequestBuilder
				.uri(URI.create(googleMapsURL + "geocode/json?address=" + sanitizedAddress + "&key=" + apiKey))
				.method("GET", HttpRequest.BodyPublishers.noBody())
				.build();

		// Parse the response to get the place ID
		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		JSONParser parser = new JSONParser();
		String placeID = "";
		try {
			JSONObject obj = (JSONObject) (parser.parse(response.body()));
			JSONArray resultsArray = (JSONArray) (obj.get("results"));
			if (resultsArray.size() > 1) {
				logger.warning("More than one response for place with address " + sanitizedAddress);
			} else if (resultsArray.size() == 0) {
				logger.warning("No place found for " + sanitizedAddress);
			} else {
				JSONObject firstResult = (JSONObject) resultsArray.get(0);
				placeID = (String) firstResult.get("place_id");
			}

		} catch (ParseException pe) {
			logger.severe(pe.getMessage());
		}
		return placeID;
	}

	JSONObject mapsMatrixRequest(List<String> placesToTry, List<String> selectedPlaces) {
		String origins = "place_id:" + String.join("%7Cplace_id:", placesToTry);
		String destinations = "place_id:" + String.join("%7Cplace_id:", selectedPlaces);
		HttpRequest request = httpRequestBuilder
			.uri(URI.create(googleMapsURL + "distancematrix/json?key=" + apiKey + "&origins=" + origins + "&destinations=" + destinations))
			.method("GET", HttpRequest.BodyPublishers.noBody())
			.build();

		HttpResponse<String> response;
		JSONParser parser = new JSONParser();
		try {
			response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
			return (JSONObject) (parser.parse(response.body()));
		} catch (IOException | InterruptedException | ParseException e) {
			e.printStackTrace();
		}

		HashMap<String, String> errorResponse = new HashMap<String, String>();
		errorResponse.put("status", "error");
		return new JSONObject(errorResponse);
	}
}
