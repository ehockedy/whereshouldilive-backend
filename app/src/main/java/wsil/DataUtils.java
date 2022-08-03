package wsil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.postgresql.ds.PGSimpleDataSource;

// A collection of functions for adding and updating data in the database
public class DataUtils {
    // Place ID is the best way to refer to places when doing distance API calls. Since not provided with data set (because
	// not google data), we must do a lookup here and store to the database.
	// Given a DB to connect to, this function creates a column for place_id and then uses city and country (currently hard-coded to UK)
	// to look up the place ID.
	public static void UpdatePlaceIds(PGSimpleDataSource ds, String apiKey) {
		try {
			Connection con = ds.getConnection();
			
			// Create a column for place_ID if does not already exist
			// This will hold the google maps place ID
			Statement st = con.createStatement();
			st.executeUpdate("ALTER TABLE places ADD COLUMN IF NOT EXISTS place_id VARCHAR(255)");
	
			ResultSet citiesRS = st.executeQuery("SELECT city FROM places");
			GoogleMapsApiHandler mapsHandler = new GoogleMapsApiHandler(apiKey);
			while (citiesRS.next()) {
				try {
					// Add "UK" after city name to avoid duplicates out of the UK
					// In future, if wanted to expand to rest of world, could append country from
					// the DB row
					String city = citiesRS.getString("city");
					String addr = city + ",UK";
					String place_id = mapsHandler.getPlaceIdFromAddress(addr);
					
					// Now that we have the place_id, write it to the table
					// Create second connection since nested statement
					Connection con2 = ds.getConnection();
					Statement st2 = con2.createStatement();
					st2.executeUpdate("UPDATE places\n"
							+ "SET place_id = '" + place_id + "'\n"
							+ "WHERE city = '" + city + "'");
					System.out.println("Place ID for " + addr + " " + place_id);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} catch (SQLException ex) {
			Logger lgr = Logger.getLogger(WhereShouldILive.class.getName());
			lgr.log(Level.SEVERE, ex.getMessage(), ex);
		}
	}
}
