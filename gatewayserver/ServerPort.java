package il.co.ilrd.gatewayserver;

public enum ServerPort {
	TCP_SERVER_PORT(50000),
	UDP_SERVER_PORT(55555), 
	HTTP_SERVER_PORT(8080);
	
	private final int PORT;
	
	ServerPort(int port) {
		this.PORT = port;
	}
	
	public int getPort() {
		return PORT;
	}
}