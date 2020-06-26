package il.co.ilrd.raspi_clients;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;

import org.json.JSONException;
import org.json.JSONObject;

import il.co.ilrd.gatewayserver.ServerPort;
import il.co.ilrd.http_message.HttpBuilder;
import il.co.ilrd.http_message.HttpMethod;
import il.co.ilrd.http_message.HttpParser;
import il.co.ilrd.http_message.HttpVersion;

public class SecuritySystem implements IotDevice {
	private Map<String, Integer> updateTypesMap = new HashMap<>();
	private Map<Long, IotMessage> messagesMap = new HashMap<>();
	private PriorityBlockingQueue<IotMessage> messagesQueue = new PriorityBlockingQueue<>();
	private long messageID = 1;
	private String currentUpdateType = "Info";
	private String currentMessage = "Info message : Security system is working";  
	private String serialNumber;
	private SocketChannel socket;
	private String IP;
	private boolean keepRunning = true;
	private Thread sendingMessagesThread;
	private long RTT = 3000;
	private final static double ALPHA = 0.2;
	private final static int TIME_TO_SLEEP = 3000;
	private final static int INFO_MESSAGE_INTERVAL = 10000; 
	private final static int WARNING_MESSAGE_INTERVAL = 5000; 
	private final static int CRITICAL_MESSAGE_INTERVAL = 1000; 
	
	public SecuritySystem(String serialNumber, String IP) {
		this.serialNumber = serialNumber;
		this.IP = IP;
		initUpdateTypesMap();
	}
	
	@Override
	public void stop() {
		keepRunning = false;
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void start() {
		initTcpConnection();
		startRecieveMessages();
		startSendingMessages();
		startRetransmission();
	}
	
	@Override
	public String getSerialNum() {
		return serialNumber;
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
	
	private void initTcpConnection() {
		InetSocketAddress address;
		try {
			address = new InetSocketAddress(InetAddress.getByName(IP), ServerPort.HTTP_SERVER_PORT.getPort());
			socket = SocketChannel.open(address);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void initUpdateTypesMap() {
		updateTypesMap.put("Info", INFO_MESSAGE_INTERVAL);
		updateTypesMap.put("Warning", WARNING_MESSAGE_INTERVAL);
		updateTypesMap.put("Critical", CRITICAL_MESSAGE_INTERVAL);
	}
	
	private class SendMessages implements Runnable {
		private ByteBuffer bufferToSend = ByteBuffer.allocate(4096);
		
		@Override
		public void run() {
			while(keepRunning) {
				try {
					IotMessage iotMessage = new IotMessage(currentMessage, messageID++);
					System.out.println(createHttpMessage(iotMessage));
					sendMessage(bufferToSend, iotMessage);
					messagesMap.put(iotMessage.getMessageID(), iotMessage);
					messagesQueue.add(iotMessage);
					Thread.sleep(updateTypesMap.get(currentUpdateType));
				} catch (InterruptedException e) {
					continue;
				}
			}
			
		}
	}
	
	private ByteBuffer stringToBuffer(String str) {
		return ByteBuffer.wrap(str.getBytes(Charset.forName("UTF-8")));
	}
	
	private void sendMessage(ByteBuffer bufferToSend, IotMessage iotMessage) {
		bufferToSend = stringToBuffer(createHttpMessage(iotMessage));
		try {
			while (bufferToSend.hasRemaining()) {
				iotMessage.setTimeOfSendingMessage(System.currentTimeMillis());
				socket.write(bufferToSend);
				iotMessage.updateNextTimeToSend();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String createHttpMessage(IotMessage iotMessage) {
		String body = createBody(iotMessage);
		Map<String, String> headersMap = new HashMap<>();
		headersMap.put("Content-Type", "application/json");
		headersMap.put("Content-Length", String.valueOf(body.length()));
		
		return HttpBuilder.createHttpRequestMessage(HttpMethod.POST,
													HttpVersion.HTTP_1_1,
													"http://" + IP + ":8080",
													headersMap,
													body); 
	}
	
	private class RecieveMessages implements Runnable {
		private ByteBuffer recieverBuffer = ByteBuffer.allocate(4096);
		private static final String MESSAGE_ID = "messageID";

		
		@Override
		public void run() {
			while(keepRunning) {
				try {
					int result = socket.read(recieverBuffer);
					if(-1 == result) {
						return;
					}
					
					Led.turnOn();
					String response = new String(recieverBuffer.array(), "UTF-8");
					IotMessage currentMessage = messagesMap.get(getMessageID(response));
					if(null == currentMessage) {
						continue;
					}
					currentMessage.setTimeOfRecievingMessage(System.currentTimeMillis());
					currentMessage.updateRTT();
					System.out.println("Security system received: " + response);
					recieverBuffer.clear();
					messagesQueue.remove(messagesMap.get(getMessageID(response)));
					messagesMap.remove(getMessageID(response));
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		private long getMessageID(String messageReceived) {
			HttpParser parser = new HttpParser(messageReceived);
			JSONObject jsonObj;
			long messageID = 0;
			
			try {
				jsonObj = new JSONObject(parser.getBody().getBody());
				messageID = jsonObj.getLong(MESSAGE_ID);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			return messageID;
		}
	}
	
	private class RetransmissionThread implements Runnable {
		private ByteBuffer bufferToSend = ByteBuffer.allocate(4096);
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
							sendMessage(bufferToSend, message);
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
	private String createBody(IotMessage iotMessage) {
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
		
	public void createNoPowerEvent() throws InterruptedException {
		currentUpdateType = "Critical";
		currentMessage = "Critical message: no power";
		sendingMessagesThread.interrupt();
	}
}
