package il.co.ilrd.gatewayserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.json.JSONException;
import org.json.JSONObject;

public class UdpClient {
	public static void main(String[] args) throws IOException, ClassNotFoundException {
	byte[] data = new byte[100];
	InetAddress address = null;
	
	try {
		address = InetAddress.getLocalHost(); 
	} catch (UnknownHostException e1) {
		e1.printStackTrace(); 
	}
	
	try(DatagramSocket socket = new DatagramSocket();){
		DatagramPacket packet = new DatagramPacket(data, data.length, address, 55555);
		JSONObject messageJson = new JSONObject();
		messageJson.put("CommandKey", "IOT_UPDATE");
		messageJson.put("Data", "{\"dbName\" : \"ElectroniClass\","
				  			   + "\"rawData\" : \"'00001'|'Working'|CURRENT_TIMESTAMP\"} ");
		String jsonStr = messageJson.toString();
		ExitClient exitThread = new ExitClient(socket);
		new Thread(exitThread).start();	 
		//while(true) {
			data = jsonStr.getBytes(); 
			packet.setData(data);
			socket.send(packet);

			socket.receive(packet);
			Thread.sleep(500);
			String messageReceived = new String(packet.getData(), 0, packet.getLength());
			System.out.println("udp client received:" + messageReceived);
		//}
	}
	
	catch (SocketException e) {
		System.out.println("udp");
		return;
	} catch (InterruptedException | IOException e) {
		e.printStackTrace();
	} catch (JSONException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	}
	
	private static class ExitClient implements Runnable {
		DatagramSocket socket;
		
		public ExitClient(DatagramSocket socket) {
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
}


