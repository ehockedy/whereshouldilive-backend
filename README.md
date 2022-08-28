# Where Should I Live? backend #
Backend code for the Where Should I Live? web application. This service provides an REST endpoint that can be used to calculate a ranking of places to live based on the time to travel to a set of selected places.

## Implementation Details
- The API and model are generated via swagger codegen. See steps below to generate this code. See Swgger definition [here](app/wsil.yaml)
- The travel duration values are obtained using the Google Maps API. The `placesToLive` and `importantPlaces` arrays take any place name or location ID that can be resolved in Google Maps. See https://developers.google.com/maps/documentation/distance-matrix

## Installation
WSIL-backend requires an `application.properties` file to exist in `src/main/resources`. This holds config required to run the app, and must contain the following config:
```
api.key=GOOGLE_MAPS_API_KEY
```

## Development
Validate Swagger:
``` 
./gradlew validateSwaggerWsil
``` 

Generate Swagger:
``` 
./gradlew generateSwaggerWsil
``` 

Build:
```
./gradlew build
```

Run:
``` 
./gradlew run
``` 
The server should start on port 8080

## Example Query body
```
{
    "placesToLive": [
        "Oxford,UK",
        "Banbury,UK",
        "Bicester,UK",
        "Milton Keynes,UK"
    ],
    "importantPlaces": [
        {
            "id": "Guildford, UK",
            "visitsPerMonth" : 0.5
        },
        {
            "id": "Lewisham, UK",
            "visitsPerMonth" : 1
        },
        {
            "id": "Chester, UK",
            "visitsPerMonth" : 1
        }
    ],
    "travelModes": ["driving", "public_transport"]
}
```
