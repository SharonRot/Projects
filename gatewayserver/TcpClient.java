package il.co.ilrd.gatewayserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;

import org.json.JSONException;
import org.json.JSONObject;

public class TcpClient {
	private final static int PORT_NUM_TCP = ServerPort.TCP_SERVER_PORT.getPort();
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), PORT_NUM_TCP);
		ByteBuffer buffer = ByteBuffer.allocate(4096); 
		SocketChannel client = null;
		
			try {
				client = SocketChannel.open(address);
				
				ExitClient exitThread = new ExitClient(client);
				new Thread(exitThread).start();	
			 
				GetMessages getMessages = new GetMessages(client);
				new Thread(getMessages).start();
				JSONObject messageJson = new JSONObject();
				messageJson.put("Commandkey", "COMPANY_REGISTRATION");
				messageJson.put("Data", "{\"dbName\" : \"tadiran\","
									  + "\"sqlCommand\" : \"CREATE TABLE PaymentHistory ( " + 
									  "payment_history_id int AUTO_INCREMENT PRIMARY KEY," + 
								 	  "payment_id int NOT NULL," + 
									  "payment_date date," + 
									  "amount int NOT NULL," + 
									  "approved boolean NOT NULL)\"}"); 
									 
				String jsonStr = messageJson.toString();
				byte[] array1 = jsonStr.getBytes();
				buffer = ByteBuffer.wrap(array1);
				while (buffer.hasRemaining()) {
					client.write(buffer);
				}
				System.out.println("Tcp client send: " + jsonStr);
				buffer.clear();
			 	Thread.sleep(500);
			 	
				messageJson.put("Commandkey", "COMPANY_REGISTRATION");
				messageJson.put("Data", "{\"dbName\" : \"tadiran\","
									  + "\"sqlCommand\" : \"CREATE TABLE CompanyContacts ( " + 
									  "company_user_id int AUTO_INCREMENT PRIMARY KEY," + 
									  "contact_id int NOT NULL)\"}"); 
			 
				jsonStr = messageJson.toString();
				array1 = jsonStr.getBytes();
				buffer = ByteBuffer.wrap(array1);
				while (buffer.hasRemaining()) {
					client.write(buffer);
				}
				System.out.println("Tcp client send: " + jsonStr);
				buffer.clear();
			 	
			} catch (JSONException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
	}
	
	private static class ExitClient implements Runnable {
		SocketChannel socket;
		
		public ExitClient(SocketChannel socket) {
			this.socket = socket; 
		}
		@Override
		public void run() {
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			String input;
			
			try {
				input = reader.readLine();
				while(!input.equals("exit")) {
					input = reader.readLine();
				}
				
				socket.close();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}	
	}
	
	private static class GetMessages implements Runnable {
		SocketChannel client;
		ByteBuffer buffer = ByteBuffer.allocate(4096); 
		
		public GetMessages(SocketChannel clientSocket) {
			client = clientSocket; 
		}
		@Override
		public void run() {
			while(true) {
				try {
					int result = client.read(buffer);
					if(-1 == result) {
						return;
					}
					
					String response = new String(buffer.array(), "UTF-8");
					System.out.println("Tcp client received: " + response);
					buffer.clear();
				} catch (ClosedChannelException e1) {
					return;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}

