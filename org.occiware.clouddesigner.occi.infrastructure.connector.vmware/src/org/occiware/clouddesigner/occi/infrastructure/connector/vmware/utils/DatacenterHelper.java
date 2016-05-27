package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils;

import java.rmi.RemoteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
/**
 * Datacenter helper class for datacenter management. 
 * @author Christophe Gourdin - Inria
 *
 */
public class DatacenterHelper {
	private static Logger LOGGER = LoggerFactory.getLogger(DatacenterHelper.class);
	
	/**
	 * Find a datacenter for name.
	 * 
	 * @param folder
	 * @param name
	 * @return
	 */
	public static Datacenter findDatacenterForName(final Folder folder, final String name) {
		Datacenter dc = null;
		// Get the datacenter attribute (via getSummary for now).
		// TODO : set a specific attribute (or mixin) for that.
		try {
			dc = (Datacenter) new InventoryNavigator(folder).searchManagedEntity("Datacenter", name);
		} catch (RemoteException ex) {
			LOGGER.error("Error while searching a datacenter name : " + name + " --> " + ex.getMessage());
		}

		return dc;

	}

	/**
	 * Find the first datacenter found, if none found, null value is returned.
	 * 
	 * @param folder
	 * @return a datacenter object, if none, null value is returned.
	 */
	public static Datacenter findFirstDatacenter(final Folder folder) {
		Datacenter dc = null;
		try {
			// Search for the first datacenter found and assign it.
			ManagedEntity[] dcs = new InventoryNavigator(folder).searchManagedEntities("Datacenter");
			if (dcs != null && dcs.length > 0) {
				// Get the first.
				dc = (Datacenter) dcs[0];
			}
		} catch (RemoteException ex) {
			LOGGER.error("Error while searching a datacenter (first on tree) " + ex.getMessage());
		}
		return dc;
	}

	/**
	 * Create a new datacenter on specified folder.
	 * 
	 * @param folder
	 * @param name
	 * @return a datacenter created object if an exception is thrown, null is
	 *         returned.
	 */
	public static Datacenter createDatacenter(final Folder folder, final String name) {
		try {
			// Create a new datacenter by default :
			Datacenter dc = folder.createDatacenter("datacenter1");
			return dc;
		} catch (RemoteException ex) {
			LOGGER.error("Datacenter creation error : datacenter1 : " + folder.getName());

		}
		return null;
	}
	
}
