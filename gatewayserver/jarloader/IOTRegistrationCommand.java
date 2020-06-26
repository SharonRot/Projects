package il.co.ilrd.jarloader;

import java.sql.SQLException;

import org.json.JSONException;
import org.json.JSONObject;

import il.co.ilrd.gatewayserver.CMDFactory;
import il.co.ilrd.gatewayserver.DatabaseManagementInterface;
import il.co.ilrd.gatewayserver.FactoryCommand;
import il.co.ilrd.gatewayserver.FactoryCommandModifier;

public class IOTRegistrationCommand implements FactoryCommandModifier{
	private static Double version = 1.0;
	private static final String NAME = "IOT_USER_REGISTRATION";
	
	@Override
	public void addToFactory() {
		CMDFactory<FactoryCommand, String, Object> cmdFactory = CMDFactory.getInstance();
		cmdFactory.add("IOT_USER_REGISTRATION", (a) -> new IOTRegistrationRegistration());
	}
	
	public static Double getVersion() {
		return version;
	}
	
	public static String getName() {
		return NAME;
	}
	
	private class IOTRegistrationRegistration implements FactoryCommand{
		private final static String IOT_REG = "{\"commandType\":\"IOT registered\"}";
		private final static String SQL_COMMAND = "sqlCommand";
		
		@Override
		public String run(Object data, DatabaseManagementInterface databaseManagement) 
				throws JSONException, SQLException {
		 	String sqlCommand = ((JSONObject)data).getString(SQL_COMMAND);
			databaseManagement.createIOTEvent(sqlCommand);
			
			return IOT_REG;
		}
	}
}