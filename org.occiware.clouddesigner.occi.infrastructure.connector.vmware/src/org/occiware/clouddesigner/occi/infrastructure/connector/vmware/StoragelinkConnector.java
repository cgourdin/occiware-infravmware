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

import java.util.List;

import org.eclipse.xtext.xtext.ecoreInference.EClassifierInfo.EClassInfo.FindResult;
import org.occiware.clouddesigner.occi.AttributeState;
import org.occiware.clouddesigner.occi.OCCIFactory;
import org.occiware.clouddesigner.occi.Resource;
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
		Resource target = this.getTarget();
		if (target == null) {
			LOGGER.error("No target storage link for this storagelink.");
			
			VCenterClient.disconnect();
			return;
		}
		String datastoreName = target.getSummary();
		
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
		
		// Load the storage part.
		target.occiRetrieve();
		
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
	
	/**
	 * get attribute value with his occi key, deserve when no property value
	 * set, with Mixin attribute as it is defined by Cloud designer.
	 * 
	 * @param key
	 * @return an attribute value, null if no one is found.
	 */
	public String getAttributeValueByOcciKey(String key) {
		String value = null;
		if (key == null) {
			return value;
		}

		List<AttributeState> attrs = this.getAttributes();
		for (AttributeState attr : attrs) {
			if (attr.getName().equals(key)) {
				value = attr.getValue();
				break;
			}
		}

		return value;

	}
	/**
	 * Create an attribute without add this to the current connector object.
	 * @param name
	 * @param value
	 * @return AttributeState object.
	 */
	public AttributeState createAttribute(final String name, final String value) {
		AttributeState attr = OCCIFactory.eINSTANCE.createAttributeState();
		attr.setName(name);
		attr.setValue(value);
		return attr;	
	}
	/**
     * Get an attribute state object for key parameter.
     * @param key ex: occi.core.title.
     * @return an AttributeState object, if attribute doesnt exist, null value is returned.
     */
    private AttributeState getAttributeStateObject(final String key) {
    	AttributeState attr = null;
    	if (key == null) {
    		return attr;
    	}
    	// Load the corresponding attribute state.
    	for (AttributeState attrState : this.getAttributes()) {
    		if (attrState.getName().equals(key)) {
    			attr = attrState;
    			break;
    		}
    	}
    	
    	return attr;
    }

}	
