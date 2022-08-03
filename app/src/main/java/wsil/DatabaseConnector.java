package wsil;

import org.postgresql.ds.PGSimpleDataSource;

public class DatabaseConnector {
    public static PGSimpleDataSource CreateDatabaseSource(String username, String password) {
		// TODO do not hard code db server, port or name
		PGSimpleDataSource ds = new PGSimpleDataSource();
		String[] serverNames = new String[] { "localhost" };
		int[] portNumbers = new int[] { 5432 };
		ds.setServerNames(serverNames);
		ds.setPortNumbers(portNumbers);
		ds.setDatabaseName("whereshouldilive");
		ds.setUser(username);
		ds.setPassword(password);
		return ds;
	}
}
