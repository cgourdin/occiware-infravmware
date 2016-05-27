package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils;

import java.net.MalformedURLException;
import java.rmi.RemoteException;

public class VMWareMain {

	
	public static void main(String[] args) {
		
		if (args != null) {
			if (args.length > 2) {
				String login = args[0];
				String password = args[1];
				String url = args[2];
				VCenterClient.setLogin(login);
				VCenterClient.setPassword(password);
				VCenterClient.setUrl(url);	
			}
		}
		try {		
			VCenterClient.connect();
			VCenterClient.disconnect();
		} catch (RemoteException | MalformedURLException ex) {
			System.out.println("Not connected, message: " + ex.getMessage());
			exit();
		}
	}
	
	private static void exit() {
		Runtime.getRuntime().exit(1);
	}
	
	
	
	
	
}
