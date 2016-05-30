package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Properties;

import org.occiware.mart.MART;

import com.vmware.vim25.mo.ServiceInstance;

/**
 * Credentials of a VCenter.
 * @author Christophe Gourdin - Inria
 *
 */
public class VCenterClient {
	
	private static String url = null;
	private static String login = null;
	private static String password = null;
	
	private static ServiceInstance serviceInstance = null;
	
	/**
	 * Init credentials from property file object.
	 * @throws IOException
	 */
	public static void init() throws IOException {
		if (url == null) {
			Properties prop = new Properties();
			
			String credentialFile = "/resources/credential.properties";
			
			prop.load(VCenterClient.class.getClassLoader().getResourceAsStream(credentialFile));
			// VCenterClient.class.getClassLoader().getResourceAsStream(credentialFile);
			if (prop.containsKey("vcenter.url")) { 
				// prop.load(in);
				login = prop.getProperty("vcenter.login");
				password = prop.getProperty("vcenter.password");
				url = prop.getProperty("vcenter.url");
				
			} else {
				throw new FileNotFoundException("credential property file not found !");
			}
		}
	}
	
	
	public static String getLogin() {
		return login;
	}
	
	public static String getPassword() {
		return password;
	}
	
	public static String getUrl() {
		return url;
	}


	public static void setUrl(String url) {
		VCenterClient.url = url;
	}


	public static void setLogin(String login) {
		VCenterClient.login = login;
	}


	public static void setPassword(String password) {
		VCenterClient.password = password;
	}
	
	/**
	 * Connect to vcenter and ready to go for action with ServiceInstance object.
	 * @param url
	 * @param login
	 * @param password
	 * @return
	 * @throws RemoteException
	 * @throws MalformedURLException
	 */
	public static ServiceInstance initServiceInstance(final String url, final String login, final String password) throws RemoteException, MalformedURLException {
		serviceInstance = new ServiceInstance(new URL(url), login, password, true); // TODO : add a parameter for certificate support --> false on last parameter.
		return serviceInstance;
	}
	
	public static ServiceInstance getServiceInstance() {
		return serviceInstance;
	}
	
	/**
	 * Initialize a connection to a vCenter server, if set, logout and connect.
	 * @throws RemoteException
	 * @throws MalformedURLException
	 */
	public static void connect() throws RemoteException, MalformedURLException {
		if (serviceInstance == null && login != null) {
			initServiceInstance(url, login, password);
		} else {
			if (serviceInstance.getAboutInfo().getApiVersion() == null) {
				// Reconnect...
				serviceInstance.getServerConnection().logout();
				if (login != null) {
					initServiceInstance(url, login, password);
				}
			}
		}
	}
	
	/**
	 * Disconnect from this vCenter server.
	 * @throws RemoteException
	 * @throws MalformedURLException
	 */
	public static void disconnect() {
		
		if (serviceInstance != null && login != null) {
			serviceInstance.getServerConnection().logout();
			serviceInstance = null;
		}
	}
	
	/**
	 * 
	 * @return
	 */
	public static boolean isConnected() {
		boolean result = false;
		if (serviceInstance != null && serviceInstance.getAboutInfo().getApiVersion() != null) {
			result = true;
		}
		return result;
	}
	
	/**
	 * if vcenter client connection is not set, this method will connect to
	 * vcenter.
	 */
	public static boolean checkConnection() {
		if (!isConnected()) {
			try {
				init();
				connect();
				return true;
			} catch (IOException ex) {
				ex.printStackTrace();
				return false;
			}
		} else {
			return true;
		}

	}
	
}
