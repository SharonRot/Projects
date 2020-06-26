package il.co.ilrd.jarloader;

import java.sql.SQLException;

import org.json.JSONException;
import org.json.JSONObject;

import il.co.ilrd.gatewayserver.CMDFactory;
import il.co.ilrd.gatewayserver.DatabaseManagementInterface;
import il.co.ilrd.gatewayserver.FactoryCommand;
import il.co.ilrd.gatewayserver.FactoryCommandModifier;

public class IOTUpdateCommand implements FactoryCommandModifier {
	private static Double version = 1.0;
	private static final String NAME = "IOT_UPDATE";
	
	@Override
	public void addToFactory() {
		CMDFactory<FactoryCommand, String, Object> cmdFactory = CMDFactory.getInstance();
		cmdFactory.add("IOT_UPDATE", (a) -> new IOTUpdate());
	}
	
	public static Double getVersion() {
		return version;
	}
	
	public static String getName() {
		return NAME;
	}
	
	private class IOTUpdate implements FactoryCommand{
		private final static String IOT_UPDATED = "iot updated";
		private final static String COMMAND_TYPE = "commandType";
		private final static String MESSAGE_ID = "messageID";
		private final static String RAW_DATA = "rawData";
		
		@Override
		public String run(Object data, DatabaseManagementInterface databaseManagement) 
				throws JSONException, SQLException {
 			String rawData = ((JSONObject)data).getString(RAW_DATA);
 			JSONObject messageToReturn = new JSONObject();
 			messageToReturn.put(COMMAND_TYPE, IOT_UPDATED);
 			messageToReturn.put(MESSAGE_ID, ((JSONObject)data).getLong(MESSAGE_ID));
			databaseManagement.createIOTEvent(rawData);
			System.out.println(messageToReturn.toString());
			return messageToReturn.toString();
		}
	}
}