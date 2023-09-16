# Where Should I Live? backend #
Backend code for the Where Should I Live? web application. This service provides an REST endpoint that can be used to calculate a ranking of places to live based on the time to travel to a set of selected places.

## Implementation Details
- The API and model are generated via swagger codegen. See steps below to generate this code. See Swgger definition [here](https://github.com/ehockedy/wsil-shared/blob/main/wsil.yaml)
- The travel duration values are obtained using the Google Maps API. The `placesToLive` and `importantPlaces` arrays take any place name or location ID that can be resolved in Google Maps. See https://developers.google.com/maps/documentation/distance-matrix

## Installation
WSIL-backend requires an `application.properties` file to exist in `src/main/resources`. This holds config required to run the app, and must contain the following config:
```
api.key=GOOGLE_MAPS_API_KEY
```
Alternatively, the API key can be read from an envirnment variable named WSIL_GMAPS_API_KEY

## Development
Validate Swagger:
``` 
./gradlew openApiValidate
``` 

Generate Swagger:
``` 
./gradlew openApiGenerate
``` 

Build:
```
./gradlew build
./gradlew build -Pserverless // If building for AWS Lambda
```

Run:
``` 
./gradlew run
``` 
If build in server mode, the server should start on port 8080

## Example Query body
```
{
    "placesToLive": [
        "ChIJJTcn0yzDdkgRobE0ieoazrM",
        "ChIJ5xOOaNPUcEgRquyg7y5e_-A"
    ],
    "importantPlaces": [
        {
            "id": "ChIJ31mWt6_EdUgRoajr-bKZZpQ",
            "visitsPerMonth" : 1
        },
        {
            "id": "ChIJafWcYtnBekgRn_jYjbNsYkk",
            "visitsPerMonth" : 1
        }
    ],
    "travelModes": ["driving", "public_transport"],
    "latLng": {
        "lat": 51.7520,
        "lng": 1.2577
    }
}
```
