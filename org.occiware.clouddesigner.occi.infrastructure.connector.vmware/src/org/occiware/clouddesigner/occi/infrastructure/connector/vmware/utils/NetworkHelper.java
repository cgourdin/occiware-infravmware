/**
 * Copyright (c) 2016 Inria
 *  
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * - Christophe Gourdin <christophe.gourdin@inria.fr>
 *
 */
package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualE1000;
import com.vmware.vim25.VirtualE1000e;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualHardware;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VirtualPCNet32;
import com.vmware.vim25.VirtualVmxnet;
import com.vmware.vim25.VirtualVmxnet2;
import com.vmware.vim25.VirtualVmxnet3;
import com.vmware.vim25.mo.VirtualMachine;

/**
 * Helper for network operations.
 * @author Christophe Gourdin - Inria.
 *
 */
public class NetworkHelper {
	
	private static Logger LOGGER = LoggerFactory.getLogger(NetworkHelper.class);
	
	public static final String NETWORK = "Network";
	public static final String HOST_NETWORK_SYSTEM = "HostNetworkSystem";
	public static final String HOST_FIREWALL_SYSTEM = "HostFirewallSystem";
	public static final String HOST_SNMP_SYSTEM = "HostSnmpSystem";
	public static final String HOST_SERVICE_SYSTEM = "HostServiceSystem";
	public static final String HOST_VMOTION_SYSTEM = "HostVMotionSystem";
	
	
	
	/**
	 * Find a list of ethernet device for a vm with the specified hostnetworkname. 
	 * @param hostNetworkName
	 * @param vm
	 * @return {@link List}{@link VirtualEthernetCard} if none, empty list is returned.
	 */
	public static List<VirtualEthernetCard> findNetDeviceForHostNetName(final String hostNetworkName, VirtualMachine vm) {
		List<VirtualEthernetCard> vEths = new ArrayList<>();
		VirtualMachineConfigInfo config = vm.getConfig();
        VirtualHardware hw = config.getHardware();
		VirtualDevice[] devices = hw.getDevice();
		for (VirtualDevice device : devices) {
			if (device instanceof VirtualEthernetCard) {
				VirtualEthernetCard vEth = (VirtualEthernetCard) device;
				VirtualDeviceBackingInfo properties = vEth.getBacking(); 
                VirtualEthernetCardNetworkBackingInfo nicBacking = (VirtualEthernetCardNetworkBackingInfo) properties;
                if (nicBacking != null && nicBacking.getDeviceName().equals(hostNetworkName)) {
                	// Device is in hostNetworkName field.
                	vEths.add(vEth);
                }
			}
		}
		
		return vEths;
	}
	
	/**
	 * Return the type of virtual device. (E1000, PCnet32, vmxnet etc.)
	 * @param vEth
	 * @return the network adapter type or "unknown" if newer types.  
	 */
	public static String getVirtualDeviceAdapterType(VirtualEthernetCard vEth) {
		String type = null;
		if (vEth instanceof VirtualE1000) {
            type = "E1000";
        } else if (vEth instanceof VirtualE1000e) {
            type = "E1000E";
        } else if (vEth instanceof VirtualPCNet32) {
        	type = "PCnet32";
        } else if (vEth instanceof VirtualVmxnet) {
            type = "Vmxnet";
        } else if (vEth instanceof VirtualVmxnet2) {
            type = "Vmxnet2";
        } else if (vEth instanceof VirtualVmxnet3) {
            type = "Vmxnet3";
        } else {
        	type = "unknown";
        }
		
		return type;
	}
	

}
