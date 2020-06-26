package il.co.ilrd.gatewayserver;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class Main {
	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException, IllegalAccessException, 
	IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException {
		GatewayServer server = new GatewayServer();
		server.addTcpServer(ServerPort.TCP_SERVER_PORT);
		server.addUdpServer(ServerPort.UDP_SERVER_PORT);
		server.addLowLevelHttpServer(ServerPort.HTTP_SERVER_PORT);
		Thread thread = new Thread(server);
		thread.start();  
	}
}
