package il.co.ilrd.jarloader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarLoader { 
	private static final String SLASH = "/";
	private static final String DOT = ".";
	private static final String CLASS = ".class";
	private static final String FILE = "file:\\";
	
	public static List<Class<?>> load(String interfaceName, String jarPath) throws ClassNotFoundException, IOException {
		List<Class<?>> classList = new ArrayList<>(); 
		try (JarFile jarFile = new JarFile(jarPath)) {
			URLClassLoader classLoader = new URLClassLoader(new URL[] { new URL(FILE + jarPath)});
			Enumeration<JarEntry> entries = jarFile.entries();
			
			while(entries.hasMoreElements()){
				 JarEntry entry = entries.nextElement();
				 
	             if(isClassFile(entry)){
	                 Class<?> currentClass = Class.forName(getClassName(entry), false, classLoader);
	                 
	                 if(checkIfClassImplementsInterfac(interfaceName, currentClass)) {
	                	 classList.add(currentClass);
	                 }
	             }
			}
				
			return classList;
		}
	}

	private static String getClassName(JarEntry entry) {
		String className;
		className = entry.getName();
		 className = removeExtensionClass(className).replaceAll(SLASH, DOT);
		return className;
	}

	private static boolean isClassFile(JarEntry entry) {
		return !entry.isDirectory() && checkIfExtensionIsClass(entry);
	}
	 
	private static boolean checkIfExtensionIsClass(JarEntry entry) {
        if(entry.getName().endsWith(CLASS)){
       	 	return true;
        }
        
        return false;
	}
	
	private static String removeExtensionClass(String str) {
		str = str.substring(0, str.lastIndexOf(DOT));
		
		return str;
	}
	
	private static boolean checkIfClassImplementsInterfac(String interfaceName, Class<?> currentClass) {
		 String currentInterface;
		 
         for(Class<?> element : currentClass.getInterfaces()) {
        	 currentInterface = element.getName();
        	 if(interfaceName.equals(currentInterface.substring(currentInterface.lastIndexOf(DOT) + 1))){
        		 return true;
        	 }
         }
         
         return false;
	}
}
