package il.co.ilrd.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;

import il.co.ilrd.pingpong.handlers.ProtocolType;
import il.co.ilrd.pingpong.handlers.ServerMessage;

public class Client2 {
	private final static int PORT_NUM_TCP = ProtocolPort.CHAT_PROTOCOL_PORT.getPort();
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		final String USER_NAME = "dan";
		ChatServerMessage innerMessage = new ChatServerMessage(null, null);
		ServerMessage messageToSend = new ServerMessage(ProtocolType.CHAT_SERVER, innerMessage);
		InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), PORT_NUM_TCP);
		ByteBuffer buffer = ByteBuffer.allocate(4096); 
		SocketChannel client = null;
		
		try {
			client = SocketChannel.open(address);
		} catch (IOException e1) {
			e1.printStackTrace();
		}  
		
		ExitClient exitThread = new ExitClient(client);
		new Thread(exitThread).start();	
		
		GetMessages getMessages = new GetMessages(client);
		new Thread(getMessages).start();
		
		innerMessage.setKey(ChatProtocolKeys.REGISTRATION_REQUEST);
		innerMessage.setData(USER_NAME);
		byte[] array1 = ServerMessage.toByteArray(messageToSend);
		buffer = ByteBuffer.wrap(array1);
		client.write(buffer);
		System.out.println("Tcp client send: " + ServerMessage.toObject(buffer.array()));
		buffer.clear();
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
					System.out.println("Tcp client received: " + ServerMessage.toObject(buffer.array()));
					buffer.clear();
				} catch (ClosedChannelException e1) {
					return;
				} catch (ClassNotFoundException | IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}