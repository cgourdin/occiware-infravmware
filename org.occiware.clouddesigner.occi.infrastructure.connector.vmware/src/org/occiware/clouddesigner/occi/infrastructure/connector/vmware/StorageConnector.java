/**
 * Copyright (c) 2016 Inria
 *  
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * - Philippe Merle <philippe.merle@inria.fr>
 * - Christophe Gourdin <christophe.gourdin@inria.fr>
 * 
 * Generated at Tue May 10 13:08:38 CEST 2016 from platform:/plugin/org.occiware.clouddesigner.occi.infrastructure/model/Infrastructure.occie by org.occiware.clouddesigner.occi.gen.connector
 */
package org.occiware.clouddesigner.occi.infrastructure.connector.vmware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.occiware.clouddesigner.occi.Link;
import org.occiware.clouddesigner.occi.Resource;
import org.occiware.clouddesigner.occi.infrastructure.StorageStatus;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.allocator.AllocatorImpl;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.DatacenterHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.DatastoreHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VCenterClient;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VolumeHelper;

/**
 * Connector implementation for the OCCI kind: - scheme:
 * http://schemas.ogf.org/occi/infrastructure# - term: storage - title: Storage
 * Resource
 */
public class StorageConnector extends org.occiware.clouddesigner.occi.infrastructure.impl.StorageImpl {
	/**
	 * Initialize the logger.
	 */
	private static Logger LOGGER = LoggerFactory.getLogger(StorageConnector.class);
	/**
	 * Datastore name, referenced on creation or on retrieve method.
	 */
	private String datastoreName = null;
	private String datacenterName = null;
	private String clusterName = null;

	/**
	 * Constructs a storage connector.
	 */
	StorageConnector() {
		LOGGER.debug("Constructor called on " + this);
	}

	//
	// OCCI CRUD callback operations.
	//

	/**
	 * Called when this Storage instance is completely created.
	 */
	@Override
	public void occiCreate() {

		LOGGER.debug("occiCreate() called on " + this);
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		ServiceInstance si = VCenterClient.getServiceInstance();
		Folder rootFolder = si.getRootFolder();

		AllocatorImpl allocator = new AllocatorImpl(rootFolder);

		StoragelinkConnector stLink;
		EList<Link> links = this.getLinks();
		List<ComputeConnector> computes = new ArrayList<>();
		if (links.isEmpty()) {
			LOGGER.warn("No storage link found, the volume is not linked to a compute.");
		} else {
			// Search for a datastore name on links.
			this.setDatastoreName(this.findDatastoreNameOnLinks());
			// Get the linked computes instance.
			computes = this.getLinkedComputes();
		}

		Float size = this.getSize();
		String volumeName = this.getTitle();
		Datacenter datacenter = null;
		Datastore datastore = null;

		if (datastoreName != null) {
			// Load datastore object.
			datastore = DatastoreHelper.findDatastoreForName(datacenter, datastoreName);
			// Search the datacenter with revert list of datastores.
			datacenter = DatacenterHelper.findDatacenterFromDatastore(rootFolder, datastoreName);
			if (datacenter == null) {
				LOGGER.error("Cannot create disk : " + volumeName + " , cause: datacenter not found for the datastore: "
						+ datastore.getName());
				VCenterClient.disconnect();
				return;
			}
			if (datastore == null) {
				LOGGER.error("Cant allocate a datastore, cause: datastore is referenced but not found on vcenter, name of the datastore: " + datastoreName);
				VCenterClient.disconnect();
				return;
			}
		} else {
			// Datastore is null, we must assign a datacenter and a
			// datastore to continue.
			
			// Searching on linked vm instance if any.
			for (ComputeConnector compute : computes) {
				datacenterName = compute.getDatacenterName();
				
				if (datacenterName != null) {
					// get the datastoreName.
					datastoreName = compute.getDatastoreName();
					if (datastoreName != null) {
						// Load the objects.
						datacenter = DatacenterHelper.findDatacenterForName(rootFolder, datacenterName);
						datastore = DatastoreHelper.findDatastoreForName(datacenter, datastoreName);
						break;
					}
				}
			}
			

			// if none found, and no links, we allocate automaticly the
			// datastore and the parent datacenter.
			if (datastore == null || datacenter == null) {
				datacenter = allocator.allocateDatacenter();
				if (datacenter == null) {
					LOGGER.error("Cant allocate a datacenter, cause : no available datacenter to allocate.");
					VCenterClient.disconnect();
					return;
				}
				datastore = allocator.allocateDatastore();
				if (datastore == null) {
					LOGGER.error("Cant allocate a datastore, cause: no available datastore to allocate.");
					VCenterClient.disconnect();
					return;
				}
			}
		}
		String vmName = null;
		// if almost one compute is linked, we get its name. 
		for (ComputeConnector compute : computes) {
			vmName = compute.getTitle();
			break;
		}
		
		// Load the volume information. If the volume doesnt exist, the volume object will be null.
		VolumeHelper.loadVolumeInformation(datastore, volumeName, datacenter, vmName);
		
		// Check if the volume already exist in the vcenter.
		if (VolumeHelper.isExistVolumeForName(datastore, volumeName, datacenter, vmName)) {
			// The volume already exist.
			LOGGER.warn("Volume : " + volumeName + " already exist in datacenter.");
			VCenterClient.disconnect();
			return;
		}

		// Create a new disk with or without vm information.
		VolumeHelper.createVolume(datacenter, datastore, volumeName, this.getSize());
		
		
		// In all case invoke a disconnect from vcenter.
		VCenterClient.disconnect();
	}

	/**
	 * Called when this Storage instance must be retrieved.
	 */
	@Override
	public void occiRetrieve() {
		LOGGER.debug("occiRetrieve() called on " + this);

		// TODO: Implement this callback or remove this method.
	}

	/**
	 * Called when this Storage instance is completely updated.
	 */
	@Override
	public void occiUpdate() {
		LOGGER.debug("occiUpdate() called on " + this);

		// TODO: Implement this callback or remove this method.
	}

	/**
	 * Called when this Storage instance will be deleted.
	 */
	@Override
	public void occiDelete() {
		LOGGER.debug("occiDelete() called on " + this);

		// TODO: Implement this callback or remove this method.
	}

	//
	// Storage actions.
	//

	/**
	 * Implement OCCI action: - scheme:
	 * http://schemas.ogf.org/occi/infrastructure/storage/action# - term: online
	 * - title: Set storage online
	 */
	@Override
	public void online() {
		LOGGER.debug("Action online() called on " + this);

		// Storage State Machine.
		switch (getState().getValue()) {

		case StorageStatus.ONLINE_VALUE:
			LOGGER.debug("Fire transition(state=online, action=\"online\")...");

			// TODO Implement transition(state=online, action="online")

			break;

		case StorageStatus.OFFLINE_VALUE:
			LOGGER.debug("Fire transition(state=offline, action=\"online\")...");

			// TODO Implement transition(state=offline, action="online")

			break;

		case StorageStatus.ERROR_VALUE:
			LOGGER.debug("Fire transition(state=error, action=\"online\")...");

			// TODO Implement transition(state=error, action="online")

			break;

		default:
			break;
		}
	}

	/**
	 * Implement OCCI action: - scheme:
	 * http://schemas.ogf.org/occi/infrastructure/storage/action# - term:
	 * offline - title: Set storage offline
	 */
	@Override
	public void offline() {
		LOGGER.debug("Action offline() called on " + this);

		// Storage State Machine.
		switch (getState().getValue()) {

		case StorageStatus.ONLINE_VALUE:
			LOGGER.debug("Fire transition(state=online, action=\"offline\")...");

			// TODO Implement transition(state=online, action="offline")

			break;

		case StorageStatus.OFFLINE_VALUE:
			LOGGER.debug("Fire transition(state=offline, action=\"offline\")...");

			// TODO Implement transition(state=offline, action="offline")

			break;

		case StorageStatus.ERROR_VALUE:
			LOGGER.debug("Fire transition(state=error, action=\"offline\")...");

			// TODO Implement transition(state=error, action="offline")

			break;

		default:
			break;
		}
	}

	/**
	 * Usage with Mixin in future.
	 * 
	 * @return
	 */
	public String getDatacenterName() {
		if (datacenterName == null) {
			// TODO : Search with mixin (or resources, depends on the
			// infrastructure extension choice).

		}

		return datacenterName;
	}

	/**
	 * Usage with Mixin in future.
	 * 
	 * @return
	 */
	public void setDatacenterName(String datacenterName) {
		this.datacenterName = datacenterName;
	}

	/**
	 * Usage with mixin in future.
	 * 
	 * @return
	 */
	public String getDatastoreName() {
		return datastoreName;
	}

	/**
	 * Usage with Mixin in future.
	 * 
	 * @return
	 */
	public void setDatastoreName(String datastoreName) {
		this.datastoreName = datastoreName;
	}

	/**
	 * Usage with Mixin in future. Must have attributes (a lot...)
	 * 
	 * @return
	 */
	public String getClusterName() {
		return clusterName;
	}

	/**
	 * Usage with Mixin in future.
	 * 
	 * @return
	 */
	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}

	/**
	 * Return a list of linked compute connector.
	 * 
	 * @return a list of ComputeConnector or empty if no linked instance.
	 */
	private List<ComputeConnector> getLinkedComputes() {

		EList<Link> links = this.getLinks();
		List<ComputeConnector> computes = new ArrayList<>();

		Resource source;
		Resource target;
		ComputeConnector instance;
		for (Link link : links) {
			source = link.getSource();
			target = link.getTarget();

			if (source != null && source instanceof ComputeConnector) {
				instance = (ComputeConnector) source;
				computes.add(instance);
			}
			if (target != null && target instanceof ComputeConnector) {
				instance = (ComputeConnector) target;
				computes.add(instance);
			}
		}

		return computes;
	}

	/**
	 * Find a datastore name on first storageLinks, title attribute.
	 * 
	 * @return a datastoreName if found, null if no links found or title
	 *         attribute on storageLink is null or empty.
	 */
	private String findDatastoreNameOnLinks() {
		String dsName = null;
		for (Link link : this.getLinks()) {
			if (link instanceof StoragelinkConnector) {
				dsName = link.getTitle();
				if (dsName != null && dsName.isEmpty()) {
					dsName = null;
					continue;
				}
				break;
			}
		}
		return dsName;
	}

}
