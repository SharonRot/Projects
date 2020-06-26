package il.co.ilrd.raspi_clients;

public interface IotDevice {
	public void stop();
	public void start();
	public String getSerialNum();
}
