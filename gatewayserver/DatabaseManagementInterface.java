package il.co.ilrd.gatewayserver;

import java.sql.SQLException;

public interface DatabaseManagementInterface {
	public void createTable(String sqlCommand) throws SQLException;
	public void createRow(String sqlCommand) throws SQLException;
	public void createIOTEvent(String rawData) throws SQLException;
}
