package il.co.ilrd.chatserver;

import java.io.Serializable;
import il.co.ilrd.pingpong.handlers.Message;

public class ChatServerMessage implements Message<ChatProtocolKeys, String> , Serializable{
	private static final long serialVersionUID = 1L;
	private ChatProtocolKeys key;
	private String data;
	
	public ChatServerMessage (ChatProtocolKeys key, String data) {
		this.key = key;
		this.data = data;
	}
	
	@Override
	public ChatProtocolKeys getKey() {
		return key;
	}
	
	@Override
	public String getData() {
		return data;
	}
	
	@Override
	public String toString() {
		return "key = " + key + " data = " + getData();
	}
	
	public void setData(String data) {
		this.data = data;
	}
	
	public void setKey(ChatProtocolKeys key) {
		this.key = key;
	}
	
	public void setMessage(ChatProtocolKeys key, String data) {
		setKey(key);
		setData(data);
	}
}
