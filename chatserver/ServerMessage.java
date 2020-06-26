package il.co.ilrd.chatserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class ServerMessage implements Message<ProtocolType, Message<?, ?>>, Serializable {
	private static final long serialVersionUID = 1L;
	private ProtocolType key;
	private Message<?, ?> data;
	
	public ServerMessage(ProtocolType key, Message<?, ?> innerMessageKey) {
		this.key = key;
		data = innerMessageKey;
	}
	
	@Override
	public ProtocolType getKey() {
		return key;
	}

	@Override
	public Message<?, ?> getData() {
		return data;
	}
	
	@Override
	public String toString() {
		return "key = " + key + " data = " + getData();
	}
	
	public static class PingPongMessage implements Message<String, Void>, Serializable {
		private static final long serialVersionUID = 1L;
		String Key;
		
		public PingPongMessage(String Key) {
			this.Key = Key;
		}
		
		@Override
		public String getKey() {
			return Key;
		}

		@Override
		public Void getData() {
			return null;
		}
		
		public String toString() {
			return getKey();
		}
	}

	public static byte[] toByteArray(Object obj) throws IOException {
		byte[] bytes = null;
        try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
        	ObjectOutputStream oos = new ObjectOutputStream(bos)){
            oos.writeObject(obj);
            oos.flush();
            bytes = bos.toByteArray();
        }
        
        return bytes;
    }
	
	public static Object toObject(byte[] bytes) throws ClassNotFoundException, IOException {
		Object obj = null;
        try(ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        	ObjectInputStream ois = new ObjectInputStream(bis);) {
            obj = ois.readObject();
        }

        return obj;
	}
}
