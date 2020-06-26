package il.co.ilrd.chatserver;

public interface Message<K, D> {
	public K getKey();
	public D getData();
}
