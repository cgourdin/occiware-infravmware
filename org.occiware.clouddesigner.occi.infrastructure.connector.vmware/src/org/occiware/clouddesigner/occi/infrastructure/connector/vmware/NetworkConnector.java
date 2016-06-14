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

import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mox.VirtualMachineDeviceManager;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.occiware.clouddesigner.occi.Attribute;
import org.occiware.clouddesigner.occi.AttributeState;
import org.occiware.clouddesigner.occi.Link;
import org.occiware.clouddesigner.occi.Mixin;
import org.occiware.clouddesigner.occi.Resource;
import org.occiware.clouddesigner.occi.infrastructure.NetworkInterfaceStatus;
import org.occiware.clouddesigner.occi.infrastructure.NetworkStatus;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions.DatacenterNotFoundException;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions.DatastoreNotFoundException;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.DatacenterHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.DatastoreHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.NetworkHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VCenterClient;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VMHelper;

/**
 * Connector implementation for the OCCI kind:
 * - scheme: http://schemas.ogf.org/occi/infrastructure#
 * - term: network
 * - title: Network Resource
 */
public class NetworkConnector extends org.occiware.clouddesigner.occi.infrastructure.impl.NetworkImpl
{
	/**
	 * Initialize the logger.
	 */
	private static Logger LOGGER = LoggerFactory.getLogger(NetworkConnector.class);

	private String vmName = null;
	private String networkAdapterName = null;
	/**
	 * Host network name.
	 */
	private String hostNetworkName = null;
	
	/**
	 * Constructs a network connector.
	 */
	NetworkConnector()
	{
		LOGGER.debug("Constructor called on " + this);
	}

	//
	// OCCI CRUD callback operations.
	//

	/**
	 * Called when this Network instance is completely created.
	 */
	@Override
	public void occiCreate()
	{
		LOGGER.debug("occiCreate() called on " + this);
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		
		
		
		
		VCenterClient.disconnect();

	}

	/**
	 * Called when this Network instance must be retrieved.
	 */
	@Override
	public void occiRetrieve()
	{
		LOGGER.debug("occiRetrieve() called on " + this);
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		
		// Load virtual machine if any.
		// Note: vmName is set with this method.
		VirtualMachine vm = getVirtualMachineFromLinks(); 
		
		if (vm == null) {
			LOGGER.warn("The linked virtual machine doesnt exist on Vcenter, no network to retrieve.");
			// No vm adapter found so.
			VCenterClient.disconnect();
			return;
		}
 		
		// Get the linked Network interface connector.
		NetworkinterfaceConnector netIntConn = getLinkedNetworkInterfaceForVM();
		
		networkAdapterName = this.getTitle();
		
		if (networkAdapterName != null && networkAdapterName.isEmpty()) {
			networkAdapterName = null;
		}
		if (hostNetworkName != null && hostNetworkName.isEmpty()) {
			hostNetworkName = null;
		}
		
		// Search the appropriate adapter if vm exist on vcenter.
		getVMHostNetworkName(vm, netIntConn);
		List<VirtualEthernetCard> vEths = null;
		VirtualEthernetCard vEthDevice = null;
		if (hostNetworkName != null) {
			// Search after network adapter name for the backing name : hostNetworkName.
			// List of all virtual ethernet card on the vm.
			vEths = NetworkHelper.findNetDeviceForHostNetName(hostNetworkName, vm);
			if (vEths.isEmpty()) {
				LOGGER.warn("No network adapter found for this host network: " + hostNetworkName);
				VCenterClient.disconnect();
				return;
			}

			
		} else {
			LOGGER.warn("The host network name is not found on vcenter, no network to retrieve.");
			VCenterClient.disconnect();
			return;
		}
		
		
		for (VirtualEthernetCard vEth : vEths) {
			if (networkAdapterName == null) {
				LOGGER.info("The network adapter is not set (title attribute), searching info on vcenter...");
				// Find the first on the list.
				networkAdapterName = vEth.getDeviceInfo().getLabel();
				vEthDevice = vEth;
				break;
			} else {
				if (vEth.getDeviceInfo().getLabel().equals(networkAdapterName)) {
					networkAdapterName = vEth.getDeviceInfo().getLabel();
					vEthDevice = vEth;
					break;
				}

			}
		}
		
		
		// Set the informations...
		this.setTitle(networkAdapterName);
		String[] ipAddressesLocal;
		String ipAddressPlainLocal = "";
		// String dnsName;
		
		if (VMHelper.isToolsInstalled(vm) && VMHelper.isToolsRunning(vm)) {
			// Get guest information.
			GuestNicInfo[] guestNicInf = vm.getGuest().getNet();
			int i;
			if (guestNicInf != null) {
				for (GuestNicInfo nicInfo : guestNicInf) {
					ipAddressesLocal = nicInfo.getIpAddress();
					i = 0;
					for (String ipAddress : ipAddressesLocal) {
						i++;
						if (i == ipAddressesLocal.length) {
							ipAddressPlainLocal += ipAddress;
						} else {
							ipAddressPlainLocal += ipAddress + ";";
						}
					}
				}
			}
			// TODO : Check if dhcp mode or other modes.
			
		}
		if (ipAddressPlainLocal.isEmpty()) {
			this.setSummary("No ip address setup.");
		} else {
			this.setSummary(ipAddressPlainLocal);
		}
		
		this.setLabel(networkAdapterName);
		
		// May be null if the device is not started...
		
		if (vEthDevice != null && vEthDevice.getConnectable() != null) {
			if (vEthDevice.getConnectable().connected) {
				this.setState(NetworkStatus.ACTIVE);
			} else {
				this.setState(NetworkStatus.INACTIVE);
			}
		}
		
		
		// Network interface part.
		List<Mixin> mixins;
		if (vEthDevice != null && netIntConn != null) {
			netIntConn.setTitle(hostNetworkName);
			netIntConn.setMac(vEthDevice.getMacAddress());
			netIntConn.setInterface(NetworkHelper.getVirtualDeviceAdapterType(vEthDevice));
			
			// TODO : Get IP Network interface mixin if any and set the attributes.
			mixins = netIntConn.getMixins();
			if (!netIntConn.getMixins().isEmpty()) {
				// TODO : Load here...
				for (Mixin mixin : mixins) {
					// TODO : How to set value here ? Object is Attribute, not AttributeState.
				}
			}
			// Set the network interface state.
			if (vEthDevice.getConnectable().connected && netIntConn != null) {
				netIntConn.setState(NetworkInterfaceStatus.ACTIVE);
			} else {
				if (netIntConn != null) {
					netIntConn.setState(NetworkInterfaceStatus.INACTIVE);
				}
			}
		}
		if (vEthDevice == null) {
			LOGGER.warn("No ethernet device found. Cant retrieve informations about network.");
			VCenterClient.disconnect();
			return;
		}
		
		VCenterClient.disconnect();
	
	}

	/**
	 * Called when this Network instance is completely updated.
	 */
	@Override
	public void occiUpdate()
	{
		LOGGER.debug("occiUpdate() called on " + this);

		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		
		
		
		
		VCenterClient.disconnect();
	}

	/**
	 * Called when this Network instance will be deleted.
	 */
	@Override
	public void occiDelete()
	{
		LOGGER.debug("occiDelete() called on " + this);

		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		
		
		
		
		VCenterClient.disconnect();
	}

	//
	// Network actions.
	//

	/**
	 * Implement OCCI action:
     * - scheme: http://schemas.ogf.org/occi/infrastructure/network/action#
     * - term: up
     * - title: 
	 */
	@Override
	public void up()
	{
		LOGGER.debug("Action up() called on " + this);

		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		
		
		
		
		
		
		
		
		// Network State Machine.
		switch(getState().getValue()) {

		case NetworkStatus.ACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=active, action=\"up\")...");

			// TODO Implement transition(state=active, action="up")

			break;

		case NetworkStatus.INACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=inactive, action=\"up\")...");

			// TODO Implement transition(state=inactive, action="up")

			break;

		case NetworkStatus.ERROR_VALUE:
			LOGGER.debug("Fire transition(state=error, action=\"up\")...");

			// TODO Implement transition(state=error, action="up")

			break;

		default:
			break;
		}
		
		
		VCenterClient.disconnect();
	}

	/**
	 * Implement OCCI action:
     * - scheme: http://schemas.ogf.org/occi/infrastructure/network/action#
     * - term: down
     * - title: 
	 */
	@Override
	public void down()
	{
		LOGGER.debug("Action down() called on " + this);
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		
		
		// Network State Machine.
		switch(getState().getValue()) {

		case NetworkStatus.ACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=active, action=\"down\")...");

			// TODO Implement transition(state=active, action="down")

			break;

		case NetworkStatus.INACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=inactive, action=\"down\")...");

			// TODO Implement transition(state=inactive, action="down")

			break;

		case NetworkStatus.ERROR_VALUE:
			LOGGER.debug("Fire transition(state=error, action=\"down\")...");

			// TODO Implement transition(state=error, action="down")

			break;

		default:
			break;
		}
		
		
		
		VCenterClient.disconnect();
	}
	
	/**
	 * Get the linked virtual machine object.
	 * @return a virtual machine object, or null if none.
	 */
	private VirtualMachine getVirtualMachineFromLinks() {
		VirtualMachine vm = null;
		List<Link> links = this.getLinks();
		ComputeConnector compute = null;
		Resource src;
		Resource target;
		for (Link link : links) {
			src = link.getSource(); 
			target = link.getTarget();
			if (src != null && src instanceof ComputeConnector) {
				compute = (ComputeConnector) src;
				break;
			}
			if (target != null && target instanceof ComputeConnector) {
				compute = (ComputeConnector) target;
				break;
			}
		}
		if (compute != null) {
			// Get vm title and search in vcenter.
			this.vmName = compute.getTitle();
			Folder rootFolder = VCenterClient.getServiceInstance().getRootFolder();
			vm = VMHelper.findVMForName(rootFolder, vmName);
		}
		
		return vm;
	}
	
	/**
	 * Get the network interface designed for this vm.
	 * @return a {@link NetworkinterfaceConnector} object, null if none.
	 */
	private NetworkinterfaceConnector getLinkedNetworkInterfaceForVM() {
		NetworkinterfaceConnector conn = null;
		if (vmName == null) {
			return conn;
		}
		List<Link> links = this.getLinks();
		Resource src;
		Resource target;
		String computeName;
		for (Link link : links) {
			if (link instanceof NetworkinterfaceConnector) {
				// Check the linked compute...
				src = link.getSource();
				target = link.getTarget();
				if (src != null && src instanceof ComputeConnector) {
					computeName = src.getTitle();
					
					if (vmName.equals(computeName)) {
						// compute found.
						conn = (NetworkinterfaceConnector) link;
						break;
					}
				}
				if (target != null && target instanceof ComputeConnector) {
					computeName = target.getTitle();
					if (vmName.equals(computeName)) {
						conn = (NetworkinterfaceConnector) link;
						break;
					}
				}
			}
			
		}
		
		
		return conn;
		
	}
	
	public String getVMHostNetworkName(VirtualMachine vm, NetworkinterfaceConnector netIntConn) {
		
		Network[] networks = null;
		try {
			networks = vm.getNetworks();
			if (networks == null) {
				// No network found. No value to retrieve.
				LOGGER.warn("No network found, no values to retrieve.");
				
				return hostNetworkName;
			}
			if (netIntConn == null) {
				// No network interface designed.
				// Search the first default interface.
				for (Network network : networks) {
					hostNetworkName = network.getName();
					break;
				}
			
			} else {
				// A network interface is defined.
				for (Network network : networks) {
					if (network.getName().equals(netIntConn.getTitle())) {
						hostNetworkName = network.getName();
						break;
					}
				}
				
			}
		
		} catch (RemoteException ex) {
			LOGGER.error("Cant get default network for vm : " + vmName);
		}
		return hostNetworkName;
	}

}	
