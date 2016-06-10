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

import org.eclipse.xtext.xtext.ecoreInference.EClassifierInfo.EClassInfo.FindResult;
import org.occiware.clouddesigner.occi.infrastructure.StorageLinkStatus;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.DatastoreHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VCenterClient;

/**
 * Connector implementation for the OCCI kind:
 * - scheme: http://schemas.ogf.org/occi/infrastructure#
 * - term: storagelink
 * - title: StorageLink Link
 */
public class StoragelinkConnector extends org.occiware.clouddesigner.occi.infrastructure.impl.StoragelinkImpl
{
	/**
	 * Initialize the logger.
	 */
	private static Logger LOGGER = LoggerFactory.getLogger(StoragelinkConnector.class);

	public static String DATASTORE = "Datastore";
	
	/**
	 * Constructs a storagelink connector.
	 */
	StoragelinkConnector()
	{
		LOGGER.debug("Constructor called on " + this);
	}

	//
	// OCCI CRUD callback operations.
	//

	/**
	 * Called when this Storagelink instance is completely created.
	 */
	@Override
	public void occiCreate()
	{
		LOGGER.debug("occiCreate() called on " + this);

		// TODO: Implement this callback or remove this method.
	}

	/**
	 * Called when this Storagelink instance must be retrieved.
	 */
	@Override
	public void occiRetrieve()
	{
		LOGGER.debug("occiRetrieve() called on " + this);
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		Folder rootFolder = VCenterClient.getServiceInstance().getRootFolder();
		// Find a datastore.
		String datastoreName = this.getTitle();
		if (datastoreName == null) {
			LOGGER.error("The datastore name is null, please set title attribute. Cant retrieve datastore.");
			this.setState(StorageLinkStatus.ERROR);
			VCenterClient.disconnect();
			return;
		}
		Datastore datastore = DatastoreHelper.findDatastoreForName(rootFolder, datastoreName);
		
		if (datastore == null) {
			LOGGER.error("The datastore " + datastoreName + " doesnt exist. Check your configuration.");
			this.setState(StorageLinkStatus.INACTIVE);
			VCenterClient.disconnect();
			return;
		}
		
		// Assign value.
		this.setState(StorageLinkStatus.ACTIVE);
		
		
		VCenterClient.disconnect();
		
		
	}

	/**
	 * Called when this Storagelink instance is completely updated.
	 */
	@Override
	public void occiUpdate()
	{
		LOGGER.debug("occiUpdate() called on " + this);

		// TODO: Implement this callback or remove this method.
	}

	/**
	 * Called when this Storagelink instance will be deleted.
	 */
	@Override
	public void occiDelete()
	{
		LOGGER.debug("occiDelete() called on " + this);

		// TODO: Implement this callback or remove this method.
	}

	//
	// Storagelink actions.
	//

}	
