package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.Network;

public class HostHelper {
	private static Logger LOGGER = LoggerFactory.getLogger(HostHelper.class);

	/**
	 * Search for a host system for a name.
	 * 
	 * @param folder
	 * @param name
	 * @return a hostSystem object, if not found, return null.
	 */
	public static HostSystem findHostSystemForName(final Folder folder, final String name) {
		HostSystem hostSystem = null;
		try {
			hostSystem = (HostSystem) new InventoryNavigator(folder).searchManagedEntity("HostSystem", name);
		} catch (RemoteException ex) {
			LOGGER.error("Error while searching a hostSystem name: " + name + " --> " + ex.getMessage());
		}
		return hostSystem;
	}

	/**
	 * Search all hostsystems on a folder, usually the rootFolder.
	 * 
	 * @param folder
	 * @return
	 */
	public static List<HostSystem> findAllHostSystem(final Folder folder) {
		List<HostSystem> hostSystems = new ArrayList<>();

		try {
			HostSystem hostSystem;
			ManagedEntity[] hosts = (ManagedEntity[]) new InventoryNavigator(folder)
					.searchManagedEntities("HostSystem");
			if (hosts != null && hosts.length > 0) {
				for (int i = 0; i < hosts.length; i++) {
					hostSystem = (HostSystem) hosts[i];
					hostSystems.add(hostSystem);
				}
			}
		} catch (RemoteException e) {
			LOGGER.error("Error while searching all hostsystems for this folder: " + folder.getName());
		}

		return hostSystems;
	}

	/**
	 * Search all host systems on a cluster.
	 * 
	 * @param folder
	 * @return
	 */
	public static List<HostSystem> findAllHostSystemOnCluster(final ClusterComputeResource cluster) {
		List<HostSystem> hostSystems = new ArrayList<>();
		HostSystem[] hosts = cluster.getHosts();
		hostSystems = Arrays.asList(hosts);
		return hostSystems;
	}

	/**
	 * Find the first host.
	 * 
	 * @param folder
	 * @return
	 */
	public static HostSystem findFirstHostSystem(final ClusterComputeResource cluster) {
		HostSystem host = null;
		// Search for the first cluster found and assign it.
		List<HostSystem> hosts = findAllHostSystemOnCluster(cluster);
		if (hosts != null && !hosts.isEmpty()) {
			for (HostSystem hostTmp : hosts) {
				host = hostTmp;
				break;
			}
		}

		return host;
	}
	
	/**
	 * Find the first Host network.
	 */
	public static Network findFirstHostNetwork(HostSystem host) {
		Network network = null;
		if (host != null) {
			try {
				network = host.getNetworks()[0];
			} catch (RemoteException ex) {
				LOGGER.error("Error while allocating a network from host: " + host.getName());
			}
		}
		return network;
	}

}
