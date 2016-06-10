package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils;

import java.io.IOException;
import java.rmi.RemoteException;

import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.Volume;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions.AttachDiskException;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions.DetachDiskException;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions.DiskNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.ArrayOfHostDatastoreBrowserSearchResults;
import com.vmware.vim25.FileInfo;
import com.vmware.vim25.FileQuery;
import com.vmware.vim25.FileQueryFlags;
import com.vmware.vim25.HostDatastoreBrowserSearchResults;
import com.vmware.vim25.HostDatastoreBrowserSearchSpec;
import com.vmware.vim25.HostDiskDimensionsChs;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.VmDiskFileQuery;
import com.vmware.vim25.VmDiskFileQueryFilter;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.FileManager;
import com.vmware.vim25.mo.HostDatastoreBrowser;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualDiskManager;

public class VolumeHelper {
	
	private static Logger LOGGER = LoggerFactory.getLogger(VolumeHelper.class);
	
	private static Volume volume = null;
	
	/**
	 * Load or refresh volume information from vcenter.
	 * @param ds
	 * @param volumeName
	 * @param dc
	 * @param vmName (may be null if volume is not attached.)
	 */
	public static void loadVolumeInformation(Datastore ds, String volumeName, Datacenter dc, String vmName) {
		// Load a volume.
		volume = new Volume(volumeName, ds, dc, vmName);
		volume.loadVolume();
		
	}
	
	/**
	 * Is the volume exist on vcenter ?
	 * @param ds
	 * @param volumeName
	 * @param dc
	 * @param vmName (may be null if volume is not attached).
	 * @return true if volume exist, else false.
	 */
	public static boolean isExistVolumeForName(Datastore ds, String volumeName, Datacenter dc, String vmName) {
		// Search the volume on datastore.
		if (!checkVolume(volumeName)) {
			// Load the volume.
			loadVolumeInformation(ds, volumeName, dc, vmName);
		}
		
		return volume.isExist();
	}
	
	
	/**
	 * Create an empty disk if no attachment on vm or a virtual disk attached to a vm.
	 * @param dc (Datacenter)
	 * @param ds (Datastore)
	 * @param volumeName
	 * @param volumeSize
	 */
	public static void createVolume(final Datacenter dc, final Datastore ds, final String volumeName, final Float volumeSize) {
		// build a new disk.
		if (!checkVolume(volumeName)) {
			loadVolumeInformation(ds, volumeName, dc, null);
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
	 * @param dc
	 * @param ds
	 * @param vmName
	 * @throws DetachDiskException 
	 */
	public static boolean destroyDisk(final String volumeName, final Datacenter dc, final Datastore ds, final String vmName) throws DetachDiskException {
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
			if (isExistVolumeForName(ds, volumeName, dc, vmName)) {
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
	
	
}
