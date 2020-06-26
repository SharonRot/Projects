package il.co.ilrd.jarloader;

import java.sql.SQLException;

import org.json.JSONException;
import org.json.JSONObject;

import il.co.ilrd.gatewayserver.CMDFactory;
import il.co.ilrd.gatewayserver.DatabaseManagementInterface;
import il.co.ilrd.gatewayserver.FactoryCommand;
import il.co.ilrd.gatewayserver.FactoryCommandModifier;

public class ProductsRegistrationCommand implements FactoryCommandModifier{
	private static Double version = 7.0;
	private static final String NAME = "PRODUCT_REGISTRATION";
	
	@Override
	public void addToFactory() {
		CMDFactory<FactoryCommand, String, Object> cmdFactory = CMDFactory.getInstance();
		cmdFactory.add("PRODUCT_REGISTRATION", (a) -> new ProductsRegistration());
	}
	
	public static Double getVersion() {
		return version;
	}
	
	public static String getName() {
		return NAME;
	}
	
	private class ProductsRegistration implements FactoryCommand{
		private final static String PRODUCT_REG = "{\"commandType\":\"product registered!!!\"}";
		private final static String SQL_COMMAND = "sqlCommand";
		
		@Override
		public String run(Object data, DatabaseManagementInterface databaseManagement) 
				throws SQLException, JSONException{
 			String sqlCommand = ((JSONObject)data).getString(SQL_COMMAND);
			databaseManagement.createRow(sqlCommand);
			
			return PRODUCT_REG;
		}
	}
}

