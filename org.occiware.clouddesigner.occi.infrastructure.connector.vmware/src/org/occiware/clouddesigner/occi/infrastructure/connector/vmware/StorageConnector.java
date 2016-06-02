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
import com.vmware.vim25.mo.ServiceInstance;

import org.eclipse.emf.common.util.EList;
import org.occiware.clouddesigner.occi.Link;
import org.occiware.clouddesigner.occi.infrastructure.StorageStatus;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.allocator.AllocatorImpl;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.DatacenterHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.DatastoreHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VCenterClient;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VolumeHelper;

/**
 * Connector implementation for the OCCI kind:
 * - scheme: http://schemas.ogf.org/occi/infrastructure#
 * - term: storage
 * - title: Storage Resource
 */
public class StorageConnector extends org.occiware.clouddesigner.occi.infrastructure.impl.StorageImpl
{
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
	StorageConnector()
	{
		LOGGER.debug("Constructor called on " + this);
	}

	//
	// OCCI CRUD callback operations.
	//

	/**
	 * Called when this Storage instance is completely created.
	 */
	@Override
	public void occiCreate()
	{
		
		LOGGER.debug("occiCreate() called on " + this);
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		ServiceInstance si = VCenterClient.getServiceInstance();
		Folder rootFolder = si.getRootFolder();
		
		AllocatorImpl allocator = new AllocatorImpl(rootFolder);
		
		// Determine if this is a main storage, if this is the case, delegate to occiCreate on compute, not here.
		StoragelinkConnector stLink;
		EList<Link> links = this.getLinks();
		
		if (links.isEmpty()) {
			LOGGER.warn("No storage link found, the volume is not linked to a compute.");
		} else {
			// TODO : Get the storage link to mount the disk and the datastore name (attribute title).
		}
		
		Float size = this.getSize();
		String volumeName = this.getTitle();
		
		Datacenter datacenter = DatacenterHelper.findDatacenterForName(rootFolder, this.getDatacenterName());
		if (datacenter == null) {
			// Allocate automaticly the datacenter, if no datacenter found, a
			// default datacenter will be created.
			datacenter = allocator.allocateDatacenter();
			if (datacenter == null) {
				LOGGER.error("Cant allocate a datacenter, cause : no available datacenter to allocate.");
				VCenterClient.disconnect();
				return;
			}
		} else {
			allocator.setDc(datacenter);
		}
		
		this.setDatacenterName(datacenter.getName());
		
		// Check if the volume already exist in the vcenter.
		if (VolumeHelper.isExistVolumeForName(datacenter, volumeName)) {
			// The volume already exist.
			LOGGER.warn("Volume : " + volumeName + " already exist in datacenter.");
			VCenterClient.disconnect();
			return;
		}
		
		Datastore datastore = null;
		// Check if the volume already exist in the vcenter.
		if (datastoreName == null) {
			// Allocate a datastore.
			datastore = allocator.allocateDatastore();
		} else {
			// Load datastore object.
			datastore = DatastoreHelper.findDatastoreForName(datacenter, datastoreName);
		}
		
		
		// In all case invoke a disconnect from vcenter.
		VCenterClient.disconnect();
	}

	/**
	 * Called when this Storage instance must be retrieved.
	 */
	@Override
	public void occiRetrieve()
	{
		LOGGER.debug("occiRetrieve() called on " + this);

		// TODO: Implement this callback or remove this method.
	}

	/**
	 * Called when this Storage instance is completely updated.
	 */
	@Override
	public void occiUpdate()
	{
		LOGGER.debug("occiUpdate() called on " + this);

		// TODO: Implement this callback or remove this method.
	}

	/**
	 * Called when this Storage instance will be deleted.
	 */
	@Override
	public void occiDelete()
	{
		LOGGER.debug("occiDelete() called on " + this);

		// TODO: Implement this callback or remove this method.
	}

	//
	// Storage actions.
	//

	/**
	 * Implement OCCI action:
     * - scheme: http://schemas.ogf.org/occi/infrastructure/storage/action#
     * - term: online
     * - title: Set storage online
	 */
	@Override
	public void online()
	{
		LOGGER.debug("Action online() called on " + this);

		// Storage State Machine.
		switch(getState().getValue()) {

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
	 * Implement OCCI action:
     * - scheme: http://schemas.ogf.org/occi/infrastructure/storage/action#
     * - term: offline
     * - title: Set storage offline
	 */
	@Override
	public void offline()
	{
		LOGGER.debug("Action offline() called on " + this);

		// Storage State Machine.
		switch(getState().getValue()) {

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

}	
