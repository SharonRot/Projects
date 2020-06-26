package il.co.ilrd.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Server implements Runnable {
	private ConnectionHandler connectionHandler = new ConnectionHandler();
	private MessageHandler messageHandler = new MessageHandler();
	private boolean isRunning = false;
	
	@Override
	public void run() { 
		isRunning = true;
		InputDetector exitDetector = new InputDetector();
		new Thread(exitDetector).start();
		try {
			connectionHandler.startConnections();
		} catch (ClosedSelectorException e1) {
			return;
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
		}	
	}

	public void stopServer() {
		isRunning = false;
		
		try {
			connectionHandler.stopConnections();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void addTcpConnection(int portNumber) throws UnknownHostException, IOException {
		checkIfServerIsRunning();
		checkIfPortNumberIsValid(portNumber);
		Connection tcpConnection = new TcpConnection(portNumber);
		connectionHandler.addConnection(tcpConnection);
	}
	
	public void addUdpConnection(int portNumber) throws IOException {
		checkIfServerIsRunning();
		checkIfPortNumberIsValid(portNumber);
		Connection udpConnection = new UdpConnection(portNumber);
		connectionHandler.addConnection(udpConnection);
	}
	
	public void addBroadcastConnection(int portNumber) throws IOException {
		checkIfServerIsRunning();
		checkIfPortNumberIsValid(portNumber);
		Connection broadcastConnection = new BroadcastConnection(portNumber);
		connectionHandler.addConnection(broadcastConnection);
	}
	
	private void checkIfServerIsRunning() {
		if(true == isRunning) {
			throw new CannotAddConnectionWhileServerIsRunning();
		}
	}
	
	private void checkIfPortNumberIsValid(int portNumber) {
		for(Connection connection : connectionHandler.connectionsList) {
			if(portNumber == connection.getPortNumber()) {
				throw new PortNumberIsAlreadyInUse();
			}
		}
	}

/**********************************Connection Interface**************************************************/

	private interface Connection {
		public void sendMessage(ClientInfo clientInfo, ByteBuffer buffer) throws IOException;
		public void communicateWithClient(Channel clientChannel, ByteBuffer buffer) throws IOException, ClassNotFoundException;
		public void initConnection(Selector selector) throws IOException;
		public int getPortNumber();
		public Channel getSocket();
	}

/******************************TCP Connection********************************************************/
	private class TcpConnection implements Connection {
		private final int portNumber;
		private ServerSocketChannel tcpSocket;
		
		public TcpConnection(int portNumber) {
			this.portNumber = portNumber;
		}
		
		public void communicateWithClient(Channel clientChannel, ByteBuffer buffer) throws IOException, ClassNotFoundException {
			int bytes = ((SocketChannel)clientChannel).read(buffer);
	    	if (-1 == bytes) {
	    		clientChannel.close();
	    	}
	     	
	    	else {
	    		ClientInfo clientInfo = new ClientInfo(clientChannel);
	    		messageHandler.handleMessage(buffer, clientInfo);
	    	}
		}
	
		@Override
		public void sendMessage(ClientInfo clientInfo, ByteBuffer buffer) throws IOException {
			while (buffer.hasRemaining()) {
				((SocketChannel)clientInfo.clientChannel).write(buffer);
			}
			
			buffer.clear();
		}
		
		@Override
		public void initConnection(Selector selector) throws IOException {
			tcpSocket = ServerSocketChannel.open();
			tcpSocket.bind(new InetSocketAddress(InetAddress.getLocalHost(), portNumber));
			tcpSocket.configureBlocking(false);
			tcpSocket.register(selector, SelectionKey.OP_ACCEPT);
		}
		
		@Override
		public int getPortNumber() {
			return portNumber;
		}

		@Override
		public ServerSocketChannel getSocket() {
			return tcpSocket;
		}
	}
	/*****************************UDP Connection*******************************************************/
	private class UdpConnection implements Connection {
		private final int portNumber;
		private DatagramChannel udpSocket;
		
		public UdpConnection(int portNumber) {
			this.portNumber = portNumber;
		}
		
		@Override
		public void communicateWithClient(Channel clientChannel, ByteBuffer buffer) throws IOException, ClassNotFoundException {
			SocketAddress clientAddress = ((DatagramChannel)clientChannel).receive(buffer);
			ClientInfo clientInfo = new ClientInfo(clientChannel, clientAddress);
		    messageHandler.handleMessage(buffer, clientInfo);
		}
		
		@Override
		public void sendMessage(ClientInfo clientInfo, ByteBuffer buffer) throws IOException {
			udpSocket.send(buffer, clientInfo.udpClientAddress);
			buffer.clear();
		}
		
		@Override
		public void initConnection(Selector selector) throws IOException {
			udpSocket = DatagramChannel.open();
			udpSocket.socket().bind(new InetSocketAddress(portNumber));
			udpSocket.configureBlocking(false);
			udpSocket.register(selector, SelectionKey.OP_READ);
		}
		
		@Override
		public int getPortNumber() {
			return portNumber;
		}

		@Override
		public DatagramChannel getSocket() {
			return udpSocket;
		}
	}

/***************************Broadcast Connection***********************************************/
	private class BroadcastConnection extends UdpConnection {
		
		public BroadcastConnection(int portNumber) {
			super(portNumber);
		}
	}
/********************************** Connection Handler*****************************************/
	private class ConnectionHandler {
		private Selector selector;
		private List<Connection> connectionsList = new LinkedList<>();
		private HashMap<Channel, Connection> channelConnectionsMap = new HashMap<>();
		private ByteBuffer buffer;
		private static final int BUFFER_SIZE = 1024;
		private static final int TIME_TO_WAIT = 10000;
		
		private void startConnections() throws IOException, ClassNotFoundException, ClosedSelectorException {
			buffer = ByteBuffer.allocate(BUFFER_SIZE);
			selector = Selector.open();
			
			for(Connection connection : connectionsList) {
				connection.initConnection(selector);
				channelConnectionsMap.put(connection.getSocket(), connection);
			}
			
			while (true) {
				if (0 == selector.select(TIME_TO_WAIT)) {
					System.err.println("Server is running");
					continue;
				};
				
		        Set<SelectionKey> selectedKeys = selector.selectedKeys();
		        Iterator<SelectionKey> iter = selectedKeys.iterator();

		        while (iter.hasNext()) {
		        	SelectionKey key = iter.next();
		        	Channel channel = key.channel();
		        	
		        	if (key.isAcceptable()) {
		        		registerTcpClient(selector, (ServerSocketChannel)channel);
		            }

		        	if (key.isReadable()) {
		        		channelConnectionsMap.get(channel).communicateWithClient(channel, buffer);
		        	}
		        	
		        	iter.remove();
		        }  	
		   }
		}

		private void stopConnections() throws IOException {
			Iterator<SelectionKey> keys = selector.keys().iterator();
			
			while(keys.hasNext()) {
				Channel channelToClose = keys.next().channel();
				channelToClose.close();
			}
			
			selector.close();	
		}

		private void addConnection(Connection connection) {
			connectionsList.add(connection);
		}
		
		private void registerTcpClient(Selector selector, ServerSocketChannel tcpSocket) throws IOException {
			SocketChannel client = tcpSocket.accept();
			channelConnectionsMap.put(client, channelConnectionsMap.get(tcpSocket));
			client.configureBlocking(false);
			client.register(selector, SelectionKey.OP_READ);
		}
	}
/**************************** Protocol************************************************/

	private interface Protocol {
		public void handleMessage(ClientInfo clientInfo, Message<?, ?> message, ByteBuffer buffer) 
				throws IOException, ClassNotFoundException;
	}


/***********************Chat protocol************************************************************/
	private class ChatProtocol implements Protocol {
		private List<ChatClientInfo> clientsList = new LinkedList<>();
		private ChatServerMessage innerMessage = new ChatServerMessage(null, null);
		private ServerMessage messageToSend = new ServerMessage(ProtocolType.CHAT_SERVER, (Message<?, ?>) innerMessage);
		private ByteBuffer buffer;
		private static final String ACCEPT_MESSAGE = "Your request has been accepted";
		private static final String INVALID_USERNAME = "User name is already in use";
		private static final String ALREADY_REGISTERED = "You are alredy registered";
		private static final String WRONG_PORT_NUM = "Port number is wrong";
		private static final String INVALID_KEY = "key is not valid";
		private static final String NEW_MEMBER = " has been joined";
		private static final String NOT_REGISTERED = "You are not registered";
		private static final String LEAVE_MESSAGE = " left the chat";
		
		@Override
		public void handleMessage(ClientInfo clientInfo, Message<?, ?> message, ByteBuffer buffer)
			throws IOException, ClassNotFoundException {
			this.buffer = buffer;
			
			if((ProtocolPort.CHAT_PROTOCOL_PORT.getPort() !=
			   (connectionHandler.channelConnectionsMap.get(clientInfo.getClientChannel())).getPortNumber())) {
				sendErrorMessage(clientInfo, WRONG_PORT_NUM);
						
				return;
			}
			
			switch ((ChatProtocolKeys)message.getKey()) {
				case REGISTRATION_REQUEST :
					handleRegistration((String) message.getData(), clientInfo);
					break;
				
				case MESSAGE :
					handleChatMessage(clientInfo, (String)message.getData());
					break;
					
				case REMOVE_REQUEST :
					handleRemoveRequest(clientInfo);
					break;
				
				default:
					sendErrorMessage(clientInfo, INVALID_KEY);
			}
		}
		
		private void handleChatMessage(ClientInfo clientInfo, String message) throws IOException {
			ChatClientInfo sender = getInfoOfTheSender(clientInfo.getClientChannel());
			if (null == sender) {
				sendErrorMessage(clientInfo, NOT_REGISTERED);
			}
			else {
				sendMessageToUsers(ChatProtocolKeys.BROADCAST_MESSAGE, 
								   sender.getClientName() + " : " +  message,
								   sender);
			}
		}
		
		private void handleRegistration(String userName, ClientInfo clientInfo) throws IOException {
			for(ChatClientInfo client : clientsList) {
				if(client.getClientName().equals(userName)) {
					sendRefuseMessage(clientInfo, INVALID_USERNAME);
					return;
				}
				else if(client.getClientChannel().equals(clientInfo.getClientChannel())) {
					sendRefuseMessage(clientInfo, ALREADY_REGISTERED);
					return;
				}
			}
			
			ChatClientInfo newClient = new ChatClientInfo(userName, clientInfo);
			clientsList.add(newClient);
			sendMessageToUsers(ChatProtocolKeys.NEW_CLIENT_REGISTRATION, 
							   newClient.getClientName() + NEW_MEMBER, 
							   newClient);
			innerMessage.setMessage(ChatProtocolKeys.REGISTRATION_ACK, ACCEPT_MESSAGE);
			fillBufferWithMessage(messageToSend);
			(connectionHandler.channelConnectionsMap.get(clientInfo.clientChannel))
													.sendMessage(clientInfo, buffer);
		}
		
		private void handleRemoveRequest(ClientInfo clientInfo) throws IOException {
			ChatClientInfo clientToRemove = getInfoOfTheSender(clientInfo.getClientChannel());
			if (null == clientToRemove) {
				sendErrorMessage(clientInfo, NOT_REGISTERED);
			}
			else {
				sendMessageToUsers(ChatProtocolKeys.BROADCAST_MESSAGE, 
								   clientToRemove.getClientName() + LEAVE_MESSAGE, 
								   clientToRemove);
				clientsList.remove(clientToRemove);
				connectionHandler.channelConnectionsMap.remove(clientInfo.clientChannel);
				clientToRemove.getClientChannel().close();
			}
		}
		
		private void sendRefuseMessage(ClientInfo clientInfo, String message) throws IOException {
			innerMessage.setMessage(ChatProtocolKeys.REGISTRATION_REFUSE, message);
			fillBufferWithMessage(messageToSend);
			(connectionHandler.channelConnectionsMap.get(clientInfo.clientChannel)).sendMessage(clientInfo, buffer);
		}
		
		private void sendErrorMessage(ClientInfo clientInfo, String message) throws IOException {
			innerMessage.setMessage(ChatProtocolKeys.ERROR_MESSAGE, message);
			fillBufferWithMessage(messageToSend);
			(connectionHandler.channelConnectionsMap.get(clientInfo.clientChannel)).sendMessage(clientInfo, buffer);
		}
		
		private void sendMessageToUsers(ChatProtocolKeys key, String message, ChatClientInfo clientInfo) throws IOException {
			innerMessage.setMessage(key, message);
			fillBufferWithMessage(messageToSend);
			for(ChatClientInfo client : clientsList) {
				if(client.getClientChannel().equals(clientInfo.getClientChannel())) {
					continue;
				}

				(connectionHandler.channelConnectionsMap.get(clientInfo.getClientChannel()))
														.sendMessage(client.getClientInfo(), buffer);
			}	
		}
		
		private ChatClientInfo getInfoOfTheSender(Channel senderChannel) {
			for(ChatClientInfo client : clientsList) {
				if(client.getClientChannel().equals(senderChannel)) {
					return client;
				}
			}
			return null;
		}
		
		private void fillBufferWithMessage(ServerMessage message) throws IOException {
			buffer.clear();
			buffer.put(ServerMessage.toByteArray(message));
			buffer.flip();
		}
	}
/*********************** Message Handler************************************************************/
	private class MessageHandler {
		private Map<ProtocolType, Protocol> protocolsMap = new HashMap<>();
		
		public MessageHandler() {
			addProtocol(ProtocolType.CHAT_SERVER, new ChatProtocol());
		}
		
		private void handleMessage(ByteBuffer messageBuffer, ClientInfo clientInfo) 
			throws ClassNotFoundException, IOException {
			@SuppressWarnings("unchecked")
			Message<ProtocolType, Message<?, ?>> messageObj =  (Message<ProtocolType, Message<?, ?>>) ServerMessage.
																							toObject(messageBuffer.array());
			System.out.println("server recived : " + messageObj);
			Message<?, ?> innerMessage = (Message<?, ?>) messageObj.getData();
			(protocolsMap.get(messageObj.getKey())).handleMessage(clientInfo, innerMessage, messageBuffer) ;
		}

		private void addProtocol(ProtocolType index, Protocol protocol) {
			protocolsMap.put(index, protocol);
		}
	}
/************************************ClientInfo***************************************************/
	private class ClientInfo {
		private Channel clientChannel;
		private SocketAddress udpClientAddress;
		
		public ClientInfo(Channel clientChannel) {
			this.clientChannel = clientChannel;
		}
		
		public ClientInfo(Channel clientChannel, SocketAddress clientAddress) {
			udpClientAddress = clientAddress;
			this.clientChannel = clientChannel;
		}

		public Channel getClientChannel(){
			return clientChannel;
		}
	}
/************************************ChatClientInfo***************************************************/
	private class ChatClientInfo {
		private String clientName;
		private ClientInfo clientInfo;
		
		public ChatClientInfo(String clientName, ClientInfo clientInfo) {
			this.clientName = clientName;
			this.clientInfo = clientInfo;
		}

		public String getClientName() {
			return clientName;
		}
		
		public Channel getClientChannel(){
			return clientInfo.getClientChannel();
		}
		
		public ClientInfo getClientInfo() {
			return clientInfo;
		}
	}
	
	
/***************************************InputDetector*********************************************/    
	private class InputDetector implements Runnable {
		@Override
		public void run() {
			final String EXIT = "exit";
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			String input;
			
			try {
				input = reader.readLine();
				while(!input.equals(EXIT)) {
					input = reader.readLine();
				}
				
				stopServer();
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}	
	} 
/***************************************Exception*********************************************/ 	
	private class CannotAddConnectionWhileServerIsRunning extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
	
	private class PortNumberIsAlreadyInUse extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}
