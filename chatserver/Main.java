package il.co.ilrd.chatserver;

import java.io.IOException;
import java.net.UnknownHostException;

public class Main {
	public static void main(String[] args) throws UnknownHostException, IOException, ClassNotFoundException {
		Server server = new Server();
		server.addTcpConnection(55555);
		Thread thread = new Thread(server);
		thread.start();	
	}
}
