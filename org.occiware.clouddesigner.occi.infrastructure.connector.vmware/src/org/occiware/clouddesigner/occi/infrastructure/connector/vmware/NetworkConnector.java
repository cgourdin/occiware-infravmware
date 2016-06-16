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
import com.vmware.vim25.MethodFault;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mox.VirtualMachineDeviceManager;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.xtext.xtext.ecoreInference.EClassifierInfo.EClassInfo.FindResult;
import org.occiware.clouddesigner.occi.Attribute;
import org.occiware.clouddesigner.occi.AttributeState;
import org.occiware.clouddesigner.occi.Link;
import org.occiware.clouddesigner.occi.Mixin;
import org.occiware.clouddesigner.occi.Resource;
import org.occiware.clouddesigner.occi.infrastructure.NetworkInterfaceStatus;
import org.occiware.clouddesigner.occi.infrastructure.NetworkStatus;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions.DatacenterNotFoundException;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions.DatastoreNotFoundException;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.allocator.Allocator;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.allocator.AllocatorImpl;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.DatacenterHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.DatastoreHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.NetworkHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VCenterClient;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VMHelper;

/**
 * Connector implementation for the OCCI kind: - scheme:
 * http://schemas.ogf.org/occi/infrastructure# - term: network - title: Network
 * Resource
 */
public class NetworkConnector extends org.occiware.clouddesigner.occi.infrastructure.impl.NetworkImpl {
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
	NetworkConnector() {
		LOGGER.debug("Constructor called on " + this);
	}

	//
	// OCCI CRUD callback operations.
	//

	/**
	 * Called when this Network instance is completely created.
	 */
	@Override
	public void occiCreate() {
		boolean nicExist = false;
		boolean created = false;
		LOGGER.debug("occiCreate() called on " + this);
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}

		// 1 - Get vm connector link, if no vm ==> no create, vmName is set with
		// this method.
		VirtualMachine vm = getVirtualMachineFromLinks();
		if (vm == null) {
			LOGGER.warn("No virtual machine is linked on the network.");
			VCenterClient.disconnect();
			return;
		}

		// 2 - Check if this network adapter already exist.
		networkAdapterName = this.getTitle();

		if (networkAdapterName != null && networkAdapterName.isEmpty()) {
			networkAdapterName = null;
		}
		if (networkAdapterName == null) {
			LOGGER.warn("No network adapter name setted. Cant create the network.");
			VCenterClient.disconnect();
			return;
		}

		// 3 - if exist, network is not created.
		nicExist = NetworkHelper.isNICExist(networkAdapterName, vm);
		if (nicExist) {
			LOGGER.warn("This network adapter: " + networkAdapterName + " already exist for the virtual machine: "
					+ vmName);
			VCenterClient.disconnect();
			return;
		}
		Folder rootFolder = VCenterClient.getServiceInstance().getRootFolder();
		HostSystem host = VMHelper.findHostSystemForVM(rootFolder, vmName);

		// Get the linked Network interface connector.
		NetworkinterfaceConnector netIntConn = getLinkedNetworkInterfaceForVM();
		if (hostNetworkName == null && netIntConn != null) {
			hostNetworkName = netIntConn.getTitle();
		} else {

			Allocator allocator = new AllocatorImpl(rootFolder);
			allocator.setHost(host);

			Network net = allocator.allocateNetwork();

			hostNetworkName = net.getName();
		}

		// 4 - if not exist, check attributes and create the network.
		// Check the hostNetworkName...
		if (hostNetworkName == null || !NetworkHelper.isHostNetworkExist(hostNetworkName, host)) {
			LOGGER.error("Host network name doesnt exist");
			VCenterClient.disconnect();
			return;
		}

		// TODO : Manual configuration network mode (mac address).
		// TODO : Customization with ipAddress and other cool things...
		VirtualDeviceConfigSpec nicSpec = NetworkHelper.createNicSpec(hostNetworkName, networkAdapterName,
				NetworkHelper.MODE_NETWORK_ADDRESS_GENERATED, null);
		VirtualMachineConfigSpec vmConfigSpec = new VirtualMachineConfigSpec();
		VirtualDeviceConfigSpec[] nicSpecArray = { nicSpec };
		vmConfigSpec.setDeviceChange(nicSpecArray);

		// Launch the reconfig task.

		Task task;
		try {
			task = vm.reconfigVM_Task(vmConfigSpec);
			task.waitForTask();

		} catch (RemoteException | InterruptedException e) {
			LOGGER.error("Error while creating a network adapter : " + networkAdapterName + " --< to vm : " + vmName,
					e);
			LOGGER.error("Message: " + e.getMessage());
			return;
		}

		TaskInfo taskInfo;
		try {
			taskInfo = task.getTaskInfo();
			if (taskInfo.getState() != TaskInfoState.success) {
				MethodFault fault = taskInfo.getError().getFault();
				LOGGER.error(
						"Error while creating a network adapter : " + networkAdapterName + " --< to vm : " + vmName,
						fault.detail);
				LOGGER.error("Fault message: " + fault.getMessage() + fault.getClass().getName());
			} else {
				created = true;
			}
		} catch (RemoteException e) {
			LOGGER.error("Error while creating an network adapter : " + vmName + " --< to vm : " + vmName, e);
			LOGGER.error("Message : " + e.getMessage());
		}

		// 5 - Reload network information, and update accordingly the object
		// (via occiRetrieve() method.)
		if (created) {
			LOGGER.info("Network : " + networkAdapterName + " has been created.");
			occiRetrieve();
		}

		VCenterClient.disconnect();

	}

	/**
	 * Called when this Network instance must be retrieved.
	 */
	@Override
	public void occiRetrieve() {
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
			// Search after network adapter name for the backing name :
			// hostNetworkName.
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
		String externalId = null;
		for (VirtualEthernetCard vEth : vEths) {
			externalId = vEth.getExternalId();
			if (networkAdapterName == null) {
				LOGGER.info("The network adapter is not set (title attribute), searching info on vcenter...");
				if (externalId != null) {
					networkAdapterName = externalId;
				} else {
					// Find the first on the list.
					networkAdapterName = vEth.getDeviceInfo().getLabel();
				}
				vEthDevice = vEth;
				break;
			} else {
				if (vEth.getDeviceInfo().getLabel().equals(networkAdapterName)) {
					vEthDevice = vEth;
					break;
				} else if (externalId != null) {
					if (externalId.equals(networkAdapterName)) {
						vEthDevice = vEth;
					}
				}

			}
		}

		// Set the informations...
		this.setTitle(networkAdapterName);
		String[] ipAddressesLocal;
		String ipAddressPlainLocal = "";
		// String dnsName;

		if (VMHelper.isToolsInstalled(vm) && VMHelper.isToolsRunning(vm) && vEthDevice != null) {
			// Get guest information.
			GuestNicInfo[] guestNicInf = vm.getGuest().getNet();
			int i;
			int key = vEthDevice.getKey();
			if (guestNicInf != null) {
				for (GuestNicInfo nicInfo : guestNicInf) {
					ipAddressesLocal = nicInfo.getIpAddress();
					int deviceConfigId = nicInfo.getDeviceConfigId();
					LOGGER.info("Network : " + nicInfo.getNetwork());
					LOGGER.info("Device Config Id : " + deviceConfigId);
					if (deviceConfigId == key) {
						i = 0;
						for (String ipAddress : ipAddressesLocal) {
							i++;

							if (i == ipAddressesLocal.length) {
								ipAddressPlainLocal += ipAddress;
							} else {
								ipAddressPlainLocal += ipAddress + ";";
							}

						}
						break;
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

			// TODO : Get IP Network interface mixin if any and set the
			// attributes.
			mixins = netIntConn.getMixins();
			if (!netIntConn.getMixins().isEmpty()) {
				// TODO : Load here...
				for (Mixin mixin : mixins) {
					// TODO : How to set value here ? Object is Attribute, not
					// AttributeState.
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
			if (netIntConn != null) {
				netIntConn.setTitle(hostNetworkName);
			}
			this.setState(NetworkStatus.INACTIVE);

			LOGGER.warn("No ethernet device found. Cant retrieve informations about network.");
			VCenterClient.disconnect();
			return;
		}

		occiRetrieve();
		VCenterClient.disconnect();

	}

	/**
	 * Called when this Network instance is completely updated.
	 */
	@Override
	public void occiUpdate() {
		LOGGER.debug("occiUpdate() called on " + this);

		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}

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
		
		
		

		VCenterClient.disconnect();
	}

	/**
	 * Called when this Network instance will be deleted.
	 */
	@Override
	public void occiDelete() {
		LOGGER.debug("occiDelete() called on " + this);

		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}

		// Load the virtual machine.
		VirtualMachine vm = getVirtualMachineFromLinks();
		if (vm == null) {
			LOGGER.warn("No virtual machine is linked on the network.");
			VCenterClient.disconnect();
			return;
		}

		// Check if the network nic device exist.
		networkAdapterName = this.getTitle();

		if (networkAdapterName != null && networkAdapterName.isEmpty()) {
			networkAdapterName = null;
		}
		if (networkAdapterName == null) {
			LOGGER.warn("No network adapter name setted. Cant create the network.");
			VCenterClient.disconnect();
			return;
		}

		boolean nicExist = NetworkHelper.isNICExist(networkAdapterName, vm);
		if (!nicExist) {
			LOGGER.warn(
					"This network adapter: " + networkAdapterName + " doesnt exist for the virtual machine: " + vmName);
			VCenterClient.disconnect();
			return;
		}

		// Remove this device.
		// Load the eth device.
		VirtualEthernetCard vEth = NetworkHelper.findVirtualEthernetCardForVM(networkAdapterName, vm);
		if (vEth == null) {
			LOGGER.error("Cant retrieve virtual ethernet card: " + networkAdapterName
					+ " for deletion on virtual machine : " + vmName);
			VCenterClient.disconnect();
			return;
		}

		VirtualMachineConfigSpec vmConfigSpec = new VirtualMachineConfigSpec();
		VirtualDeviceConfigSpec nicSpec = new VirtualDeviceConfigSpec();
		nicSpec.setOperation(VirtualDeviceConfigSpecOperation.remove);
		nicSpec.setDevice(vEth);
		VirtualDeviceConfigSpec[] nicSpecArray = { nicSpec };
		vmConfigSpec.setDeviceChange(nicSpecArray);

		// Launch the task.
		Task task;
		try {
			task = vm.reconfigVM_Task(vmConfigSpec);
			task.waitForTask();

		} catch (RemoteException | InterruptedException e) {
			LOGGER.error("Error while deleting a network adapter : " + networkAdapterName + " --< from vm : " + vmName,
					e);
			LOGGER.error("Message: " + e.getMessage());
			return;
		}

		TaskInfo taskInfo;
		try {
			taskInfo = task.getTaskInfo();
			if (taskInfo.getState() != TaskInfoState.success) {
				MethodFault fault = taskInfo.getError().getFault();
				LOGGER.error(
						"Error while deleting a network adapter : " + networkAdapterName + " --< from vm : " + vmName,
						fault.detail);
				LOGGER.error("Fault message: " + fault.getMessage() + fault.getClass().getName());
			} else {
				LOGGER.info("The network : " + networkAdapterName + " is removed from virtual machine : " + vmName);
			}
		} catch (RemoteException e) {
			LOGGER.error("Error while creating an network adapter : " + vmName + " --< to vm : " + vmName, e);
			LOGGER.error("Message : " + e.getMessage());
		}
		VCenterClient.disconnect();
	}

	//
	// Network actions.
	//

	/**
	 * Implement OCCI action: - scheme:
	 * http://schemas.ogf.org/occi/infrastructure/network/action# - term: up -
	 * title:
	 */
	@Override
	public void up() {
		LOGGER.debug("Action up() called on " + this);

		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}

		// Network State Machine.
		switch (getState().getValue()) {

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
	 * Implement OCCI action: - scheme:
	 * http://schemas.ogf.org/occi/infrastructure/network/action# - term: down -
	 * title:
	 */
	@Override
	public void down() {
		LOGGER.debug("Action down() called on " + this);
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}

		// Network State Machine.
		switch (getState().getValue()) {

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
	 * 
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
	 * 
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

	/**
	 * Get the hostnetwork name (host global network name), or allocate an
	 * hostnetwork if no network interfaces.
	 * 
	 * @param vm
	 * @param netIntConn
	 * @return
	 */
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
				Folder rootFolder = VCenterClient.getServiceInstance().getRootFolder();
				HostSystem host = VMHelper.findHostSystemForVM(rootFolder, vmName);
				Allocator allocator = new AllocatorImpl(rootFolder);
				allocator.setHost(host);
				Network net = allocator.allocateNetwork();
				if (net != null) {
					hostNetworkName = net.getName();
				} else {
					LOGGER.error("The host " + host.getName() + " has no network.");
					return hostNetworkName;
				}

			} else {
				// A network interface is defined.
				if (netIntConn.getTitle() == null || netIntConn.getTitle().isEmpty()) {
					// No host network name is defined.
					hostNetworkName = null;
					return hostNetworkName;

				}

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
