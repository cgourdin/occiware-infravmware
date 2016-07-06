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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.Volume;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions.AttachDiskException;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions.DetachDiskException;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions.DiskNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualDeviceFileBackingInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.mo.VirtualMachine;

public class VolumeHelper {
	
	private static Logger LOGGER = LoggerFactory.getLogger(VolumeHelper.class);
	
	private static Volume volume = null;
	
	/**
	 * Load or refresh volume information from vcenter.
	 * @param dsName
	 * @param volumeName
	 * @param dcName
	 * @param vmName (may be null if volume is not attached.)
	 */
	public static void loadVolumeInformation(String dsName, String volumeName, String dcName, String vmName) {
		// Load a volume.
		volume = new Volume(volumeName, dsName, dcName, vmName);
		volume.loadVolume();
		
	}
	
	/**
	 * Is the volume exist on vcenter ?
	 * @param dsName
	 * @param volumeName
	 * @param dcName
	 * @param vmName (may be null if volume is not attached).
	 * @return true if volume exist, else false.
	 */
	public static boolean isExistVolumeForName(String dsName, String volumeName, String dcName, String vmName) {
		// Search the volume on datastore.
		if (!checkVolume(volumeName)) {
			// Load the volume.
			loadVolumeInformation(dsName, volumeName, dcName, vmName);
		}
		
		return volume.isExist();
	}
	
	
	/**
	 * Create an empty disk if no attachment on vm or a virtual disk attached to a vm.
	 * @param dcName (Datacenter name)
	 * @param dsName (Datastore name)
	 * @param volumeName
	 * @param volumeSize
	 */
	public static void createVolume(final String dcName, final String dsName, final String volumeName, final Float volumeSize) {
		// build a new disk.
		if (!checkVolume(volumeName)) {
			loadVolumeInformation(dsName, volumeName, dcName, null);
		}
		if (volume.isExist()) {
			LOGGER.warn("The disk " + volumeName + " already exist, cant create it.");
			return;
		}
		if (volume.getVmName() != null) {
			volume.createAttachedVolume();
		} else {
			volume.createEmptyVolume();
		}
	}
	
	
	
	// Delegate methods.
	/**
	 * Set the disk size on volume object.
	 * @param size
	 */
    public static void setSize(final String volumeName, final Float size) {
    	if (checkVolume(volumeName)) {
    		volume.setSize(size);
    	} 
    }
    
	public static Float getSize(String volumeName) throws DiskNotFoundException {
		if (checkVolume(volumeName)) {
			return volume.getSize();
		} else {
			LOGGER.warn("No disk information loaded, cant give a size.");
			throw new DiskNotFoundException("No disk information loaded, cant give a size.");
		}
	}
	/**
	 * 
	 * @return the volume state, null if no volume defined.
	 * @throws DiskNotFoundException
	 */
	public static String getDiskState(final String volumeName) throws DiskNotFoundException {
		if (checkVolume(volumeName)) {
			return volume.getVolumeState();
		} else {
			LOGGER.warn("No disk information loaded, cant give a state.");
			throw new DiskNotFoundException("No disk information loaded, cant give a state.");
		}
	}
	
	/**
	 * 
	 * @return
	 * @throws DiskNotFoundException
	 */
	public static boolean isAttached(final String volumeName) throws DiskNotFoundException {
		boolean result;
		if (checkVolume(volumeName)) {
			result = volume.isAttached();
		} else {
			LOGGER.warn("No disk information loaded, cant give an attachment state.");
			throw new DiskNotFoundException("No disk information loaded, cant give an attachment state.");
		}
		return result;
	}
	
	/**
	 * Resize the specified disk.
	 * @param dc
	 * @param ds
	 * @param volumeName
	 * @param vmName
	 */
	public static boolean resizeDisk(final String volumeName, Float newSize) throws DiskNotFoundException {
		boolean result = false;
		if (checkVolume(volumeName)) {
			result = volume.resize(newSize);
			
		} else {
			LOGGER.warn("No disk information loaded, cant resize the disk.");
			throw new DiskNotFoundException("No disk information loaded, cant resize the disk.");
		}
		return result;
		
	}
	
	/**
	 * Get the uuid of the disk.
	 * @param volumeName
	 * @return 
	 */
	public static String getDiskUUID(String volumeName) {
		String uuid = "unknown";
		if (checkVolume(volumeName)) {
			uuid = volume.getUUID();
		}
		return uuid;
	}
	
	
	/**
	 * Rename a disk from oldVolumeName to newVolumeName and the vmdk file accordingly.
	 * @param oldVolumeName
	 * @param newVolumeName
	 * @return true if operation succeed
	 * @throws DiskNotFoundException
	 */
	public static boolean renameDisk(final String oldVolumeName, final String newVolumeName) throws DiskNotFoundException {
		boolean result = false;
		if (checkVolume(oldVolumeName)) {
			result = volume.renameDisk(newVolumeName);
			
		} else {
			LOGGER.warn("No disk information loaded, cant rename the disk: " + oldVolumeName);
			throw new DiskNotFoundException("No disk information loaded, cant rename the disk : " + oldVolumeName);
		}
		return result;
	}
	
	/**
	 * Destroy definitively a disk from vcenter. If the disk is attached, the disk is detached before.
	 * @param volumeName
	 * @param dcName
	 * @param dsName
	 * @param vmName
	 * @throws DetachDiskException 
	 */
	public static boolean destroyDisk(final String volumeName, final String dcName, final String dsName, final String vmName) throws DetachDiskException {
		boolean result = false;
		if (checkVolume(volumeName)) {
			// The volume is loaded
			// Destroy the vmdk.
			if (volume.isAttached()) {
				// Detach the disk before the deletion.
				result = detachDisk(volumeName);
				if (!result) {
					LOGGER.warn("The disk is attached and cannot be detached ! Please, check your system after deletion.");
				}
				result = false;
			}
			result = volume.destroyVolume();
			
		} else {
			if (isExistVolumeForName(dsName, volumeName, dcName, vmName)) {
				// disk is reloaded successfully, destroy the vmdk.
				result = volume.destroyVolume();
			}
		}
		return result;
	}
	
	
	/**
	 * Detach the disk from vm instance. 
	 * @param volumeName
	 * @throws DetachDiskException 
	 */
	public static boolean detachDisk(final String volumeName) throws DetachDiskException {
		boolean result = false;
		if (checkVolume(volumeName)) {
			if (volume.isAttached()) {
				result = volume.detachVolume();
			}
		}
		return result;
	}
	
	
	/**
	 * Attach the disk from vm instance.
	 * @param volumeName
	 * @param vmName
	 * @throws AttachDiskException 
	 * @throws DetachDiskException 
	 */
	public static boolean attachDisk(final String volumeName, final String vmName) throws AttachDiskException, DetachDiskException {
		boolean result = false;
		if (checkVolume(volumeName)) {
			if (volume.isAttached()) {
				result = detachDisk(volumeName);
				if (!result && volume.isAttached()) {
					LOGGER.warn("Cant detach the disk, please check your configuration and logs.");
				}
				result = false;
			}
			
			result = volume.attachVolume();
			
			if (!result) {
				LOGGER.warn("Cant attach the disk: " + volumeName + " to: " + vmName);
			}
			
		}
		return result;
	}
	
	
	/**
	 * Check if volume object is the good one with volumeName info.
	 * @param volumeName
	 * @return true if ok, false otherwise.
	 */
	private static boolean checkVolume(final String volumeName) {
		boolean result = false;
		if (volume != null && volume.getVolumeName().equals(volumeName)) {			
				result = true;
		}
		return result;
	}
	
	/**
	 * Get all vmware virtual disk object for a VM. 
	 * @param vm
	 * @return a map of String (diskName), VirtualDisk.
	 */
	public static Map<String, VirtualDisk> getVirtualDiskForVM(VirtualMachine vm) {
		
		
		Map<String, VirtualDisk> mapDisks = new HashMap<>(); 
		if (vm == null) {
			return mapDisks;
		}
		VirtualDevice[] devices = vm.getConfig().getHardware().getDevice();
		
		String diskName;
		
		// Search on devices.
		for (VirtualDevice device : devices) {
			diskName = null;
			if (device == null) {
				continue;
			} else if (device instanceof VirtualDisk) {
				VirtualDisk disk = (VirtualDisk) device;
				VirtualDeviceBackingInfo vdbi = device.getBacking();
				
				if (vdbi instanceof VirtualDeviceFileBackingInfo) {
					diskName = ((VirtualDeviceFileBackingInfo) vdbi).getFileName();
					// Add to map.
					mapDisks.put(diskName, disk);
				}
			}
		}
		
		
		return mapDisks;
	}
	
}
