package wsil;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import org.postgresql.ds.PGSimpleDataSource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WhereShouldILive {
    public static void main(String[] args) {
        Options options = new Options();
		options.addOption(Option.builder()
				.option("k")
				.longOpt("mapsApiKey")
				.argName("google maps api key")
				.desc("API key to use for querying google maps")
				.hasArg()
				.required()
				.build());
		options.addOption(Option.builder()
				.longOpt("updatePlaceIds")
                .desc("query Google maps API to get the place ID for all places in the database")
                .build());

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cmd = parser.parse(options, args);

			// TODO get password and user passed through
			PGSimpleDataSource ds = DatabaseConnector.CreateDatabaseSource();
			if (cmd.hasOption("updatePlaceIds")) {
				String apiKey = cmd.getOptionValue("mapsApiKey");
				DataUtils.UpdatePlaceIds(ds, apiKey);

			}

			// Start the server
			SpringApplication.run(WhereShouldILive.class, args);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
    }
}
