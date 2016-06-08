package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils;

import java.io.IOException;
import java.rmi.RemoteException;

import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.DiskNotFoundException;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.Volume;
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
		if (volume == null) {
			// Load the volume.
			loadVolumeInformation(ds, volumeName, dc, vmName);
		}
		
		return volume.isExist();
	}
	
	
	/**
	 * Create an empty disk with no attachment on vm.
	 * @param dc (Datacenter)
	 * @param ds (Datastore)
	 * @param volumeName
	 * @param volumeSize
	 */
	public static void createVolume(final Datacenter dc, final Datastore ds, final String volumeName, final Float volumeSize) {
		// build a new disk.
		if (volume == null) {
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
    public static void setSize(final Float size) {
    	if (volume != null) {
    		volume.setSize(size);
    	}
    }
    
	public static Float getSize() throws DiskNotFoundException {
		if (volume != null) {
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
	public static String getDiskState() throws DiskNotFoundException {
		if (volume != null) {
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
	public static boolean isAttached() throws DiskNotFoundException {
		boolean result;
		if (volume != null) {
			result = volume.isAttached();
		} else {
			LOGGER.warn("No disk information loaded, cant give an attachment state.");
			throw new DiskNotFoundException("No disk information loaded, cant give an attachment state.");
		}
		return result;
	}
	
}
