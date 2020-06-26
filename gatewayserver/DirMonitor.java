package il.co.ilrd.gatewayserver;

import java.io.IOException;

import il.co.ilrd.observer.Callback;

public interface DirMonitor {
	public void register(Callback<String> callback);
	public void unregister(Callback<String> callback);
	public void stopUpdate() throws IOException;
}