package il.co.ilrd.gatewayserver;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import il.co.ilrd.http_message.HttpBuilder;
import il.co.ilrd.http_message.HttpParser;
import il.co.ilrd.http_message.HttpStatusCode;
import il.co.ilrd.http_message.HttpVersion;
import il.co.ilrd.jarloader.JarLoader;
import il.co.ilrd.observer.Callback;
import il.co.ilrd.observer.Dispatcher;
import il.co.ilrd.DatabaseManagement.DatabaseManagement;
import static java.nio.file.StandardWatchEventKinds.*;

public class GatewayServer implements Runnable { 
	private ThreadPoolExecutor threadPool;
	private CMDFactory<FactoryCommand, String, Object> cmdFactory = CMDFactory.getInstance();
	private ConnectionHandler connectionHandler = new ConnectionHandler();
	private MessageHandler messageHandler = new MessageHandler();
	private final static int DEFAULT_NUM_THREADS = Runtime.getRuntime().availableProcessors();
	private final static int MAX_NUM_THREADS = 10;
	private boolean isRunning = false; 
	private DatabaseHandler dbHandler = new DatabaseHandler();
	private FactoryCommandLoader commandLoader = new FactoryCommandLoader();  
	private final static String DIR_PATH = "C:\\JarDir";
	private JarMonitor commandMonitor = new JarMonitor(DIR_PATH); 
	private DirChangesAnalyzer analyzer = new DirChangesAnalyzer(); 
	private static final String INTERFACE_NAME = "FactoryCommandModifier";
	
	public GatewayServer(int numOfThreads) throws IOException, ClassNotFoundException, IllegalAccessException, 
		IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, 
		InstantiationException {
		threadPool = new ThreadPoolExecutor(numOfThreads, MAX_NUM_THREADS, 1, TimeUnit.SECONDS, 
											new LinkedBlockingQueue<Runnable>());
		initFactory();
	}
	
	public GatewayServer() throws ClassNotFoundException, IOException, IllegalAccessException, IllegalArgumentException, 
		InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException {
		this(DEFAULT_NUM_THREADS);
	}
	
	public void addLowLevelHttpServer(ServerPort portNumber) {
		checkIfServerIsRunning();
		checkIfPortNumberIsValid(portNumber.getPort());
		ServerConnection httpConnection = new LowLevelHttpServer(portNumber.getPort());
		connectionHandler.addConnection(httpConnection);
	}
	
	public void addTcpServer(ServerPort portNumber) {
		checkIfServerIsRunning();
		checkIfPortNumberIsValid(portNumber.getPort());
		ServerConnection tcpConnection = new TcpServer(portNumber.getPort());
		connectionHandler.addConnection(tcpConnection);
	}
	
	public void addUdpServer(ServerPort portNumber) {
		checkIfServerIsRunning();
		checkIfPortNumberIsValid(portNumber.getPort());
		ServerConnection udpConnection = new UdpServer(portNumber.getPort());
		connectionHandler.addConnection(udpConnection);
	}
	
	public void addHighLevelHttpServer(ServerPort portNumber) {
		checkIfServerIsRunning();
		checkIfPortNumberIsValid(portNumber.getPort());
		HighLevelHttpServer highLevelHttpServer= new HighLevelHttpServer(portNumber.getPort());
		connectionHandler.addConnection(highLevelHttpServer);
	}
	
	public void run(){
		isRunning = true;
		
		try {
			connectionHandler.startConnections();
			return;
		} catch (ClassNotFoundException |  IOException e) {
			e.printStackTrace();
		}
	}
	
	public void stop() throws IOException {
		isRunning = false;
		commandMonitor.stopUpdate();
		connectionHandler.stopConnections();
	}
	
	public void setNumOfThreads(int numOfThread) {
		threadPool.setCorePoolSize(numOfThread);
	}
	
	private void checkIfServerIsRunning() {
		if(isRunning) {
			throw new CannotAddConnectionWhileServerIsRunning();
		}
	}
	
	private void checkIfPortNumberIsValid(int portNumber) {
		for(ServerConnection connection : connectionHandler.connectionsList) {
			if(portNumber == connection.getPortNumber()) {
				throw new PortNumberIsAlreadyInUse();
			}
		}
	}

	private void initFactory() throws ClassNotFoundException, IOException, IllegalAccessException, IllegalArgumentException, 
	InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException {
		loadInitialJarFiels();
		analyzer.register(commandMonitor);
	}
	
	private void loadInitialJarFiels() throws ClassNotFoundException, IOException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException {
		File dir = new File(DIR_PATH);
		String contents[] = dir.list();
		
		for(String file: contents) {
			if (isJarFile(file)) {
				commandLoader.load(INTERFACE_NAME, file);
			}
		}
	}
	
/********************************** Connection Handler*****************************************/
	private class ConnectionHandler {
		private Selector selector;
		private List<ServerConnection> connectionsList = new LinkedList<>();
		private HashMap<Channel, ServerConnection> channelConnectionsMap = new HashMap<>();
		private ByteBuffer buffer;
		private static final int BUFFER_SIZE = 4096;
		private static final int TIME_TO_WAIT = 10000;
		
		private void startConnections() throws IOException, ClassNotFoundException, ClosedSelectorException {
			buffer = ByteBuffer.allocate(BUFFER_SIZE);
			selector = Selector.open();
			
			for(ServerConnection connection : connectionsList) {
				connection.initServerConnection(selector);
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
		        		channelConnectionsMap.get(channel).handleRequestMessage(channel, buffer);
		        	}
		        	
		        	iter.remove();
		        }  	
		   }
		}
		
		private void stopConnections() throws IOException {
			for (ServerConnection serverConnection : connectionsList) {
				serverConnection.stopServer();
			}
			
			selector.close();
		}
		
		private void addConnection(ServerConnection connection) {
			connectionsList.add(connection);
		}
		
		private void registerTcpClient(Selector selector, ServerSocketChannel tcpSocket) throws IOException {
			SocketChannel client = tcpSocket.accept();
			channelConnectionsMap.put(client, channelConnectionsMap.get(tcpSocket));
			client.configureBlocking(false);
			client.register(selector, SelectionKey.OP_READ);
		}
	}
	
/*********************** Message Handler************************************************************/
	private class MessageHandler {
		private JsonToRunnableConvertor jsonToRunnableConvertor = new JsonToRunnableConvertor();
		private static final String INVALID_REQUEST = "{\"errorMessage\":\"invalid request\"}";
		private static final String COMMAND_KEY = "CommandKey";
		private static final String DATA = "Data";

		public void handleMessage(String message, ClientInfo clientInfo) throws IOException {
			try {
				JSONObject jsonObject = new JSONObject(message);
				
				if(null == jsonObject.get(DATA) || null == jsonObject.get(COMMAND_KEY)) {
					sendMessage(clientInfo, INVALID_REQUEST);
				} else {
					threadPool.submit(jsonToRunnableConvertor.convertToRunnable(jsonObject, clientInfo));
				}
			} catch(JsonSyntaxException | JSONException e) {
				sendMessage(clientInfo, INVALID_REQUEST);
			}
		}
	}
/***************************************JsonToRunnableConvertor************************************************/	
	
	private class JsonToRunnableConvertor {
		private static final String COMMAND_KEY = "CommandKey";
		private static final String DATA = "Data";
		private static final String INVALID_REQUEST = "{\"errorMessage\":\"invalid request\"}";
		
		private Runnable convertToRunnable(JSONObject json, ClientInfo clientInfo) {
			return new Runnable() {
				@Override
				public void run() {
					String response = null;
					try {
						if(!checkIfFactoryConatainCommand(json.getString(COMMAND_KEY))){
							response = INVALID_REQUEST;
						}
						else {
							JSONObject jsonObject = new JSONObject(json.get(DATA).toString());
							response = cmdFactory.create(json.getString(COMMAND_KEY))
														.run(jsonObject, 
										 					 dbHandler.getDatabaseManagement(json.get(DATA).toString()));
						}
					} catch (SQLException | JSONException e) {
						response = INVALID_REQUEST;
					}

					sendMessage(clientInfo, response);
				};
			};
		}
		
		private boolean checkIfFactoryConatainCommand(String command) {
			return cmdFactory.containKey(command);
		}
	}
/***************************FactoryCommand***********************************************/	
	private class DatabaseHandler {
		private Map<String, DatabaseManagement> databasesMap = new HashMap<>();
		private static final String URL = "jdbc:mysql://127.0.0.1:3306/?serverTimezone=UTC";
		private static final String USER_NAME = "root";
		private static final String PASSWORD = "arui8383";
		private static final String DB_NAME = "dbName";
		
		public DatabaseManagement getDatabaseManagement(String data) throws JSONException, SQLException {
			String dbName = getDbName(data);

			try {
				createDatabaseIfNotExist(dbName);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			
			return databasesMap.get(dbName);
		}
		
		private void createDatabaseIfNotExist(String databaseName) throws SQLException, ClassNotFoundException {
			if(!databasesMap.containsKey(databaseName)) {
				DatabaseManagement dbManager;
				dbManager = new DatabaseManagement(URL, USER_NAME, PASSWORD, databaseName);
				databasesMap.put(databaseName, dbManager);		
			}
		}
		
		private String getDbName(String data) throws JSONException {
			JSONObject jsonObject = new JSONObject(data);
			return jsonObject.getString(DB_NAME);
		}
	}

/******************************FactoryCommandLoader****************************************************/
	private class FactoryCommandLoader {
		private static final String ADD_TO_FACTORY = "addToFactory";
		private static final String GET_VERSION = "getVersion";
		private static final String GET_NAME = "getName";
		private Map<String, Double> versionsMap = new HashMap<>();
		
		private void load(String interfaceName, String jarFilePath) throws ClassNotFoundException, IOException, IllegalAccessException, IllegalArgumentException, 
		InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException {
			for(Class<?> element : JarLoader.load(interfaceName, jarFilePath)) {
				if(isNewClass(element) || ifClassUpdated(element)) {
					callAddToFactory(element);
					versionsMap.put(getName(element) ,getVersion(element)); 
				}
			}
		}
		
		private boolean isNewClass(Class<?> classToCheack) 
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
			return  !versionsMap.containsKey(getName(classToCheack));
		}
		
		private boolean ifClassUpdated(Class<?> classToCheack)
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
			return versionsMap.get(getName(classToCheack)) != getVersion(classToCheack);
		}
		
		private void callAddToFactory(Class<?> addToFactoryClass) throws NoSuchMethodException, SecurityException, IllegalAccessException, 
			IllegalArgumentException, InvocationTargetException, InstantiationException {
			Method addToFactoryMethod = addToFactoryClass.getMethod(ADD_TO_FACTORY);
			addToFactoryMethod.invoke(addToFactoryClass.getConstructor().newInstance());
		}
		
		private String getName(Class<?> classObj) throws NoSuchMethodException, SecurityException,
				IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			Method getNameMethod = classObj.getMethod(GET_NAME);
			return (String)getNameMethod.invoke(null);
		}
		
		private Double getVersion(Class<?> classObj) throws NoSuchMethodException, SecurityException,
				IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			Method getVersionMethod = classObj.getMethod(GET_VERSION);
			return (Double)getVersionMethod.invoke(null);
		}
	}
/******************************FileChangesAnalyzer***************************************/
	private class DirChangesAnalyzer {
		private Callback<String> callback = new Callback<String>((filePath)-> {loadClassesFromJar(filePath);}, null);
		
		public void register(JarMonitor monitor) {
			monitor.register(callback);
		}
		
		private void loadClassesFromJar(String jarFilePath) {
			try {
				commandLoader.load(INTERFACE_NAME, jarFilePath);
			} catch (ClassNotFoundException | IOException e) {
				e.printStackTrace();
			}catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | 
						InvocationTargetException | InstantiationException	e) {
					e.printStackTrace();
			}	
		}
	}
	
/******************************JarMonitor****************************************************/	
	private class JarMonitor implements DirMonitor {
		private Dispatcher<String> dispatcher = new Dispatcher<>();
		private WatchService watcher = FileSystems.getDefault().newWatchService();
		private Path dirPath;
		private boolean isAlive = true;
		
		public JarMonitor(String dirPath) throws IOException {
			this.dirPath = Paths.get(dirPath);
			this.dirPath.register(watcher, ENTRY_MODIFY);
			startWatching();
		}
		
		@Override
		public void register(Callback<String> callback) {
			dispatcher.register(callback);
			
		}
		
		@Override
		public void stopUpdate() {
			isAlive = false;
			
			try {
				watcher.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		@Override
		public void unregister(Callback<String> callback) {
			dispatcher.unregister(callback);
		}
		
		private void startWatching() {
			new Thread((new Runnable() {
				
				@Override
				public void run() { 
					WatchKey key = null;
					Path dir;
					Path fullPath;
					
					while(isAlive) {
						try {
							key = watcher.take();
							
							if(Objects.nonNull(key)) {
								for(WatchEvent<?> event : key.pollEvents()) {
									dir = (Path)key.watchable();
									fullPath = dir.resolve((Path) event.context());

									if(isJarFile(fullPath.toString())) {
										dispatcher.updateAll(fullPath.toString());
									}
									if(!key.reset()) {
										isAlive = false;
									}
								}
							}

						}catch (InterruptedException e) {
							e.printStackTrace();
						}catch (ClosedWatchServiceException e) {
							continue;
						}
					}
				}
			})).start();
		}	
	}
/******************************ServerConnection********************************************************/
	private interface ServerConnection {
		public void initServerConnection(Selector selector) throws IOException;
		public void handleRequestMessage(Channel clientChannel, ByteBuffer buffer) throws IOException;
		public void sendResponse(ClientInfo clientInfo, ByteBuffer buffer) throws IOException;
		public int getPortNumber();
		public Channel getSocket();
		public void stopServer() throws IOException;
	}
/******************************TCP Connection********************************************************/
	private class TcpServer implements ServerConnection {
		private final int portNumber;
		private ServerSocketChannel tcpSocket;
		
		public TcpServer(int portNumber) {
			this.portNumber = portNumber;
		}
		
		public void handleRequestMessage(Channel clientChannel, ByteBuffer buffer) throws IOException {
			int bytes = ((SocketChannel)clientChannel).read(buffer);
	    	if (-1 == bytes) {
	    		clientChannel.close();
	    	} else {
	    		ClientInfo clientInfo = new ClientInfo(clientChannel, new TcpServer(portNumber));
				messageHandler.handleMessage(bufferToString(buffer), clientInfo);
				buffer.clear();
	    	}
		}
	
		@Override
		public void sendResponse(ClientInfo clientInfo, ByteBuffer buffer) throws IOException {
			while (buffer.hasRemaining()) {
				((SocketChannel)clientInfo.clientChannel).write(buffer);
			}
			
			buffer.clear();
		}
		
		@Override
		public void initServerConnection(Selector selector) throws IOException {
			tcpSocket = ServerSocketChannel.open();
			tcpSocket.bind(new InetSocketAddress(portNumber));
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

		@Override
		public void stopServer() throws IOException {
			tcpSocket.close();
		}
	}
/*****************************UDP Connection*******************************************************/
	private class UdpServer implements ServerConnection {
		private final int portNumber;
		private DatagramChannel udpSocket;
		
		public UdpServer(int portNumber) {
			this.portNumber = portNumber;
		}
		
		@Override
		public void handleRequestMessage(Channel clientChannel, ByteBuffer buffer) throws IOException {
			SocketAddress clientAddress = ((DatagramChannel)clientChannel).receive(buffer);
			ClientInfo clientInfo = new ClientInfo(clientChannel, clientAddress, this);
			messageHandler.handleMessage(bufferToString(buffer), clientInfo);
			buffer.clear();
		}
		
		@Override
		public void sendResponse(ClientInfo clientInfo, ByteBuffer buffer) throws IOException {
			udpSocket.send(buffer, clientInfo.udpClientAddress);
			buffer.clear();
		}
		
		@Override
		public void initServerConnection(Selector selector) throws IOException {
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
		
		@Override
		public void stopServer() throws IOException {
			udpSocket.close();
		}
	}
	
/***************************LowLevelHttpServer***********************************************/
	private class LowLevelHttpServer implements ServerConnection {
		private final int portNumber;
		private ServerSocketChannel tcpSocket;
		private final static String CONTENT_LENGTH = "Content-Length";
		private final static String CONTENT_TYPE = "Content-Type";
		private final static String JSON_TYPE = "application/json";
		
		public LowLevelHttpServer(int portNumber) {
			this.portNumber = portNumber;
		}
		
		@Override
		public void handleRequestMessage(Channel clientChannel, ByteBuffer buffer) throws IOException {
			int bytes = ((SocketChannel)clientChannel).read(buffer);
	    	if (-1 == bytes) {
	    		clientChannel.close();
	    	} else {
	    		String body = parseBody(buffer);
	    		ClientInfo clientInfo = new ClientInfo(clientChannel, this);
				messageHandler.handleMessage(body, clientInfo);
				buffer.clear();
	    	}
		}
	
		@Override
		public void sendResponse(ClientInfo clientInfo, ByteBuffer buffer) throws IOException {
			String body = bufferToString(buffer);
			String response = HttpBuilder.createHttpResponseMessage(HttpVersion.HTTP_1_1, 
																	HttpStatusCode.OK, 
																	getResponseHeader(body.length()),
																	body);
			buffer.clear();
			buffer = stringToBuffer(response);
			while (buffer.hasRemaining()) {
				((SocketChannel)clientInfo.clientChannel).write(buffer);
			}
			buffer.clear();
		}
		
		@Override
		public void initServerConnection(Selector selector) throws IOException {
			tcpSocket = ServerSocketChannel.open();
			tcpSocket.bind(new InetSocketAddress(portNumber));
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
		
		@Override
		public void stopServer() throws IOException {
			tcpSocket.close();
		}

		private String parseBody(ByteBuffer buffer) throws IOException {
			HttpParser parser = new HttpParser(new String(buffer.array(), "ASCII"));
			return parser.getBody().getBody();
		}
		
		private Map<String, String> getResponseHeader(int length) { 
			Map<String, String> responseHeader = new HashMap<>();
			responseHeader.put(CONTENT_LENGTH, String.valueOf(length));
			responseHeader.put(CONTENT_TYPE, JSON_TYPE);
			
			return responseHeader;
		}
	}

/***************************HighLevelHttpServer***********************************************/
	private class HighLevelHttpServer implements ServerConnection{
		private HttpServer sunHttpServer;
		private final int portNumber;
		private final static String CONTENT_TYPE = "Content-Type";
		private final static String JSON_TYPE = "application/json";
		
		public HighLevelHttpServer(int portNumber) {
			this.portNumber = portNumber;
		}
		
		@Override
		public void initServerConnection(Selector selector) throws IOException {
			sunHttpServer = HttpServer.create(new InetSocketAddress(portNumber), 0);
			sunHttpServer.createContext("/", new HttpHandler() {
				@Override
				public void handle(HttpExchange exchange) throws IOException {
					String body = convertInputStreamToString(exchange.getRequestBody());
					messageHandler.handleMessage(body, new ClientInfo(exchange, HighLevelHttpServer.this));
				}
			});
			sunHttpServer.start();
		}

		@Override
		public void handleRequestMessage(Channel clientChannel, ByteBuffer buffer) throws IOException{
			//Not implemented
		}
	
		@Override
		public void sendResponse(ClientInfo clientInfo, ByteBuffer buffer) throws IOException {
			clientInfo.getHttpExchange().getResponseHeaders().add(CONTENT_TYPE, JSON_TYPE);
			clientInfo.getHttpExchange().sendResponseHeaders(HttpStatusCode.OK.getCode(), 
															 bufferToString(buffer).length());
			OutputStream os = clientInfo.getHttpExchange().getResponseBody();
			os.write(buffer.array());
			os.close();
		}
	
		@Override
		public int getPortNumber() {
			return portNumber;
		}
	
		@Override
		public Channel getSocket() {
			//Not implemented
			return null;
		}
		
		@Override
		public void stopServer() throws IOException {
			sunHttpServer.stop(0);
		}
		
		private String convertInputStreamToString(InputStream inputStream) throws IOException {
			BufferedInputStream buffer = new BufferedInputStream(inputStream);
		    ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
		    int result = buffer.read();
		    while(result != -1) {
		      byte b = (byte)result;
		      byteArray.write(b);
		      result = buffer.read();
		    }
		    
		    return byteArray.toString();
		}
	}	
	
/************************************ClientInfo***************************************************/
	private class ClientInfo {
		private Channel clientChannel;
		private SocketAddress udpClientAddress;
		private ServerConnection connection;
		private HttpExchange httpExchange;
		
		public ClientInfo(Channel clientChannel, ServerConnection connection) {
			this.clientChannel = clientChannel;
			this.connection = connection;
		}
		
		public ClientInfo(Channel clientChannel, SocketAddress clientAddress, ServerConnection connection) {
			udpClientAddress = clientAddress;
			this.clientChannel = clientChannel;
			this.connection = connection;
		}
		
		public ClientInfo(HttpExchange httpExchange, ServerConnection connection) {
			this.httpExchange = httpExchange;
			this.connection = connection;
		}
		
		public HttpExchange getHttpExchange(){
			return httpExchange;
		}
		
		public ServerConnection getServerConnection(){
			return connection;
		} 
	}
	
/****************************************utility methods***************************************/
	private ByteBuffer stringToBuffer(String str) {
		return ByteBuffer.wrap(str.getBytes(Charset.forName("UTF-8")));
	}
	
	private String bufferToString(ByteBuffer buffer) throws IOException {
		return new String(buffer.array(), "UTF-8");
	}
	
	
	private void sendMessage(ClientInfo clientInfo, String message) {
		try {
			clientInfo.getServerConnection().sendResponse(clientInfo, stringToBuffer(message));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
		
	private boolean isJarFile(String fileName) {
		File file = new File(fileName);
		
		return fileName.endsWith(".jar") && file.isFile();
	}

/***************************************Exception*********************************************/ 	
	private class CannotAddConnectionWhileServerIsRunning extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
	
	private class PortNumberIsAlreadyInUse extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}
