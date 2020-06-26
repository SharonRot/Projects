package il.co.ilrd.gatewayserver;

import java.sql.SQLException;

import org.json.JSONException;

public interface FactoryCommand {
	public String run(Object data, DatabaseManagementInterface databaseManagement) 
			throws SQLException, JSONException;
}
