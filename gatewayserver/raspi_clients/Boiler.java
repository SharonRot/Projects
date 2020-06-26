package il.co.ilrd.raspi_clients;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;

import org.json.JSONException;
import org.json.JSONObject;

import il.co.ilrd.gatewayserver.ServerPort;

public class Boiler implements IotDevice  {
	private Map<String, Integer> updateTypesMap = new HashMap<>();
	private Map<Long, IotMessage> messagesMap = new HashMap<>();
	private PriorityBlockingQueue<IotMessage> messagesQueue = new PriorityBlockingQueue<>();
	private long messageID = 1;
	private String currentUpdateType = "Info";
	private String currentMessage = "Info message : temp = 35C";
	private String serialNumber;
	private final static int DATA_SIZE = 1024;
	private byte[] data = new byte[DATA_SIZE];
	private DatagramSocket socket; 
	private InetAddress address;
	private String IP;
	private boolean keepRunning = true;
	private Thread sendingMessagesThread;
	private long RTT = 3000;
	private final static double ALPHA = 0.2;
	private final static int TIME_TO_SLEEP = 3000;
	private final static int INFO_MESSAGE_INTERVAL = 10000; 
	private final static int WARNING_MESSAGE_INTERVAL = 5000; 
	private final static int CRITICAL_MESSAGE_INTERVAL = 1000; 
	
	public Boiler(String serialNumber, String IP) {
		this.serialNumber = serialNumber;
		this.IP = IP;
		initUpdateTypesMap();
	}

	@Override
	public void start() {
		initUdpConnection();
		startRecieveMessages();
		startSendingMessages();
		startRetransmission();
	}
	
	@Override
	public void stop() {
		keepRunning = false;
		socket.close();
	}

	@Override
	public String getSerialNum() {
		return serialNumber;
	}
	
	private void initUpdateTypesMap() {
		updateTypesMap.put("Info", INFO_MESSAGE_INTERVAL);
		updateTypesMap.put("Warning", WARNING_MESSAGE_INTERVAL);
		updateTypesMap.put("Critical", CRITICAL_MESSAGE_INTERVAL);
	}

	private void startSendingMessages() {
		sendingMessagesThread = new Thread(new SendMessages());
		sendingMessagesThread.start();
	}

	private void startRecieveMessages() {
		new Thread(new RecieveMessages()).start();	
	}
	
	private void startRetransmission() {
		try {
			Thread.sleep(TIME_TO_SLEEP);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		new Thread(new RetransmissionThread()).start();	
	}
	
	private void initUdpConnection() {
		try {
			address = InetAddress.getByName(IP);
			socket = new DatagramSocket();
		} catch (UnknownHostException | SocketException e) {
			e.printStackTrace();
		} 
	}

	private class SendMessages implements Runnable {
		private DatagramPacket packetToSend = 
				new DatagramPacket(data, data.length, address, ServerPort.UDP_SERVER_PORT.getPort());
		@Override
		public void run() {
			while(keepRunning) {
				try {
					IotMessage iotMessage = new IotMessage(currentMessage, messageID++);
					sendMessage(packetToSend, iotMessage);
					messagesMap.put(iotMessage.getMessageID(), iotMessage);
					messagesQueue.add(iotMessage);
					Thread.sleep(updateTypesMap.get(currentUpdateType));
				} catch (InterruptedException e) {
					continue;
				}
			}
		}
	}
	
	public byte[] createMessage(IotMessage iotMessage) {
		return createJsonMessage(iotMessage).getBytes(); 
	}
	
	private void sendMessage(DatagramPacket packetToSend, IotMessage iotMessage) {
		packetToSend.setData(createMessage(iotMessage));
		try {
			iotMessage.setTimeOfSendingMessage(System.currentTimeMillis());
			socket.send(packetToSend);
			iotMessage.updateNextTimeToSend();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private class RecieveMessages implements Runnable {
		private static final String MESSAGE_ID = "messageID";
		private DatagramPacket recieverPacket = 
				new DatagramPacket(data, data.length, address, ServerPort.UDP_SERVER_PORT.getPort());
		@Override
		public void run() {
			while(keepRunning) {
				try {
					socket.receive(recieverPacket);
					Led.turnOn();
					String messageReceived = new String(recieverPacket.getData(), 0, recieverPacket.getLength());
					IotMessage currentMessage = messagesMap.get(getMessageID(messageReceived));
					if(null == currentMessage) {
						continue;
					}
					currentMessage.setTimeOfRecievingMessage(System.currentTimeMillis());
					currentMessage.updateRTT();
					System.out.println("boiler received:" + messageReceived);
					messagesQueue.remove(messagesMap.get(getMessageID(messageReceived)));
					messagesMap.remove(getMessageID(messageReceived));
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		private long getMessageID(String messageReceived) {
			JSONObject jsonObj;
			long messageID = 0;
			
			try {
				jsonObj = new JSONObject(messageReceived);
				messageID = jsonObj.getLong(MESSAGE_ID);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			return messageID;
		}
	}
	
	private class RetransmissionThread implements Runnable {
		private DatagramPacket packetToSend = 
				new DatagramPacket(data, data.length, address, ServerPort.UDP_SERVER_PORT.getPort());
		private final static int MAX_COUNTER = 3;

		@Override
		public void run() {
			while(keepRunning) {
				if(!messagesQueue.isEmpty()) {
					IotMessage currentMessage = messagesQueue.peek();
					long messageID = currentMessage.getMessageID();
					if(currentMessage.getCounter() >= MAX_COUNTER) {
						messagesQueue.poll();
						messagesMap.remove(currentMessage.getMessageID());
					}
					
					else {
						try {
							Thread.sleep(calcTimeToSleep(currentMessage));
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						
						IotMessage message = messagesQueue.poll();
						if(null != message && messageID == message.getMessageID()) {
							sendMessage(packetToSend, message);
							message.updateCounter();
							message.updateNextTimeToSend();
							messagesQueue.add(message);						
						}
					}
				}
			}
		}

		private long calcTimeToSleep(IotMessage message) {
			long timeToSleep =  message.getTimeToSendMessage() - System.currentTimeMillis();
			if (timeToSleep < 0) {
				return 0;
			}
			
			return timeToSleep;
		}
	}
	
	/***********************************************
	 * IotMessage class
	 **********************************************/
	private class IotMessage implements Comparable<IotMessage> {
		private String message;
		private int repeatCounter = 0;
		private final long messageID;
		private long nextTimeToSend;
		private long timeOfSendingMessage;
		private long timeOfRecievingResponse;
		
		public IotMessage(String message, long messageID) {
			this.message = message;
			this.messageID = messageID;
			nextTimeToSend = System.currentTimeMillis() + 2 * RTT;
		}
		
		private void updateCounter() {
			++repeatCounter;	
		}
		
		private void updateNextTimeToSend() {
			nextTimeToSend = System.currentTimeMillis() + 2 * RTT;
		}
		
		private void updateRTT() {
			RTT = (long)(ALPHA * getSampleRTT() +  (1 - ALPHA) * RTT);
		}
		
		private void setTimeOfSendingMessage(long timeOfSendingMessage) {
			this.timeOfSendingMessage = timeOfSendingMessage;
		}
		
		private void setTimeOfRecievingMessage(long timeOfRecievingResponse) {
			this.timeOfRecievingResponse = timeOfRecievingResponse;
		}
		
		private long getSampleRTT() {
			return timeOfRecievingResponse - timeOfSendingMessage;
		}
		
		private String getMessage() {
			return message;
		}
		
		private long getMessageID() {
			return messageID;
		}
		
		private int getCounter() {
			return repeatCounter;
		}
		
		private long getTimeToSendMessage() {
			return nextTimeToSend;
		}

		@Override
		public int compareTo(IotMessage secondMessage) {
			return (int)(this.nextTimeToSend - secondMessage.nextTimeToSend); 
		}
	}

	/*******************************************************************************/
	
	private String createJsonMessage(IotMessage iotMessage) {
		JSONObject outerJsonMessage = new JSONObject();
		JSONObject innerJsonMessage = new JSONObject();
		final String commandKey = "CommandKey";
		final String dbName = "dbName";
		final String rawData = "rawData";
		final String data = "Data";
		final String iotUpdate = "IOT_UPDATE";
		final String messageID = "messageID";
		
		try {
			outerJsonMessage.put(commandKey, iotUpdate);
			innerJsonMessage.put(dbName, "ElectroniClass");
			innerJsonMessage.put(rawData, "'" + serialNumber + "'|'" + iotMessage.getMessage() + "'|CURRENT_TIMESTAMP");
			innerJsonMessage.put(messageID, iotMessage.getMessageID());
			outerJsonMessage.put(data, innerJsonMessage.toString());
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		return outerJsonMessage.toString();
	}
	
	public void createNoWaterEvent() throws InterruptedException {
		currentUpdateType = "Critical";
		currentMessage = "Critical message: No water";
		sendingMessagesThread.interrupt();
	}
}
