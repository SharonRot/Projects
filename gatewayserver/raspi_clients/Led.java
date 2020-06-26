package il.co.ilrd.raspi_clients;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.RaspiPin;

public class Led {
	private static GpioController gpio = GpioFactory.getInstance();
	private static GpioPinDigitalOutput pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_00);
	
    public static void turnOn() throws InterruptedException {
        pin.setState(true);
        Thread.sleep(500);
        pin.setState(false);
    }
}
