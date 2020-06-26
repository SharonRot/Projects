package il.co.ilrd.gatewayserver;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class CMDFactory<T, K, D> {
	public Map<K, Function<D, ? extends T>> map = new HashMap<>();
	
	private CMDFactory(){}
	 
	public void add(K key, Function<D, ? extends T> func) {
		map.put(key, func);
	}
 
	public T create(K key, D data) {
		return map.get(key).apply(data);
	}
	
	public T create(K key) {
		return create(key, null);	
	}
	
	public boolean containKey(K key) {
		return map.containsKey(key);
	}
	
    private static class SingletonHolder<T, K, D>{
		@SuppressWarnings("rawtypes")
		public static CMDFactory<?, ?, ?> instance = new CMDFactory();
     }
    
    @SuppressWarnings("unchecked")
	public static <T, K, D> CMDFactory<T, K, D> getInstance() {
        return (CMDFactory<T, K, D>)SingletonHolder.instance;
    }
}
