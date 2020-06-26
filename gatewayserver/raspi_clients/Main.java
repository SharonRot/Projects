package il.co.ilrd.raspi_clients;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class Main {
	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException, IllegalAccessException, 
	IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException {
		Boiler boiler = new Boiler("00001", "10.1.0.199");
		boiler.start();
		Thread.sleep(20000);
		boiler.createNoWaterEvent();
		Thread.sleep(10000);
		
		/*SmokeDetector smokeDetector = new SmokeDetector("00001", "10.1.0.199");
		smokeDetector.start();
		Thread.sleep(20000);
		smokeDetector.createFireEvent();
		Thread.sleep(10000);
		smokeDetector.stop();*/
		
		/*SecuritySystem securitySystem = new SecuritySystem("00001", "10.1.0.199");
		securitySystem.start();
		Thread.sleep(20000);
		securitySystem.createNoPowerEvent();
		Thread.sleep(10000);
		securitySystem.stop();*/
	}
}

