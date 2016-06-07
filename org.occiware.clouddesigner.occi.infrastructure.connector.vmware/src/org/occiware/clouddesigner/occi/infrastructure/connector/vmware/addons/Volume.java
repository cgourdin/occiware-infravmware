package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Calendar;

import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VCenterClient;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VMHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.ArrayOfHostDatastoreBrowserSearchResults;
import com.vmware.vim25.FileInfo;
import com.vmware.vim25.FileQuery;
import com.vmware.vim25.FileQueryFlags;
import com.vmware.vim25.HostDatastoreBrowserSearchResults;
import com.vmware.vim25.HostDatastoreBrowserSearchSpec;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualDeviceFileBackingInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VmDiskFileQuery;
import com.vmware.vim25.VmDiskFileQueryFilter;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.FileManager;
import com.vmware.vim25.mo.HostDatastoreBrowser;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

/**
 * This class define a virtual volumes as virtual disk AND as vmdk file.
 * @author Christophe Gourdin - Inria
 *
 */
public class Volume {
	private static Logger LOGGER = LoggerFactory.getLogger(Volume.class);
	public static final String VIRTUAL_DISK = "VirtualDisk";
	public static final String CONTROLLER_IDE = "VirtualIDEController";
	public static final String CONTROLLER_SCSI = "VirtualSCSIController";
	public static final String CONTROLLER_SATA = "VirtualSATAController";
	
	
	private Datacenter dc;
	private Datastore ds;
	private String volumeName;
	/**
	 * Linked virtual machine name (null if none).
	 */
	private String vmName;
	/**
	 * VI java data object.
	 */
	private VirtualDisk vdisk = null;
	/**
	 * Size is in GB.
	 */
	private Float size = 0.0f;
	/**
	 * Physical full path on VMWare infra.
	 */
	private String fullPath = null;
	/**
	 * Mount point like : /home/user/ .
	 */
	private String mountPoint = null;
	
	/**
	 * Define if this volume is the main on a compute or not.
	 */
	private boolean mainVolume = false;
	/**
	 * VMWare state of this volume.
	 */
	private String volumeState = null;
	
	/**
	 * VMWare controller type of this volume like : VirtualSCSIController, VirtualIDEController, VirtualSATAController.
	 */
	private String controllerType = CONTROLLER_SCSI; // Default to scsi controller.
	/**
	 * Define if this volume exist on vmware vcenter.
	 */
	private boolean exist = false;
	private boolean attached = false;
	
	private Calendar modifiedDate;
	/**
	 * Build a volume object from vcenter server information.
	 * @param volumeName
	 * @param ds
	 * @param dc
	 */
	public Volume(final String volumeName, final Datastore ds, final Datacenter dc, final String vmName) {
		this.volumeName = volumeName;
		this.ds = ds;
		this.dc = dc;
		this.vmName = vmName;
	}
	/**
	 * Load all the attributes of this object from vcenter. If none found, this volume doesnt exist anymore.
	 */
	public void loadVolume() {
		// 1 - Check if volume exist.
		if (fullPath == null) {
			// Load the fullPath and check if it exist.
			fullPath = findVolumeVMDKPathForName();
		} 
		if (fullPath == null) {
			LOGGER.warn("No vmware path found on this volume, cant load the volume informations.");
			exist = false;
			return;
		}
		
		exist = exists(fullPath, ds);
		if (!exist) {
			return;
		}
		
		// Load its information.
		if (vmName != null) {
			// Load the corresponding virtualDisk.
			loadVirtualDisk();
		} 
		if (vdisk != null) {
			// Get the size of this disk.
			Long sizeCapaKB = vdisk.getCapacityInKB();
			size = sizeCapaKB.floatValue() / (1024 * 1024);
			// Disk is attached.
			attached = true;
			// Determine if this volume is a main disk.
			Integer unitNumber = vdisk.getUnitNumber();
			// TODO : Check if there are other ways to know about a system disk.
			if (unitNumber == 0) {
				mainVolume = true;
			}
			
			
		} else {
			// Disk is not attached to a vm.
			attached = false;
			// this volume is not the main...
			mainVolume = false;
			// The disk size has been set on findVolumeVMDKPathForName...
		}
		
		
		
	}
	/**
	 * Load the corresponding virtual disk object if this volume is attached on vm instance.
	 */
	public void loadVirtualDisk() {
		VirtualMachine vm = VMHelper.loadVirtualMachine(vmName);
		if (vm == null) {
			LOGGER.warn("The attached virtual machine doesnt exist, cant load virtual disk information.");
			vdisk = null;
			return;
		}
		VirtualDevice[] devices = vm.getConfig().getHardware().getDevice();
		if (devices == null) {
			LOGGER.warn("No attached devices on this virtual machine : " + vmName + " --> can't load virtual disk information");
			vdisk = null;
			return;
		}
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
			    }
			    if (diskName != null && diskName.equals(fullPath)) {
			    	// The disk has been found on the virtual machine.
			    	vdisk = disk;
			    	break;
			    }
			}
		}
	}
	
	
	/**
	 * Create an empty volume on vmware vcenter, attached to a datastore but not attached on a compute.
	 * We create here a new VMDK file on the datastore with the given path.
	 */
	public void createEmptyVolume() {
		// TODO : Create empty volume and load attributes on this object.
		String dsName = ds.getName();
		fullPath = findVolumeVMDKPathForName();
		
		// Check if this volume already exist in the datastore.
		if (fullPath == null) {
			// The volume doesnt exist, we create it in a temporary directory.
			try {
				mkdir(dc, ds, "/tmp");
			} catch (IOException ex) {
				LOGGER.error("Error IO : " + ex.getMessage());
				ex.printStackTrace();
				return;
			}
		}
		// Create the volume.
		// TODO !!!
	}
	
	/**
	 * Create a new volume attached on a vm compute, after, load it's information on this object attribute.
	 * We use the method of the virtualDevice from vijava SDK.
	 */
	public void createAttachedVolume() {
		// TODO : Create attached volume and load attribute on this object.
		
	}

	/**
	 * Destroy definitively this volume from datastore and vcenter.
	 */
	public void destroyVolume() {
		// TODO : Detroy this volume definitively.
	}
	
	
	/**
	 * Attach the volume to an instance. Dont forget to load virtualDisk object from vcenter and update vmdk path.
	 * if this volume is already attached, this method does nothing.
	 */
	public void attachVolume() {
		// TODO : Attach volume method.
	}
	
	/**
	 * Detach this volume from the instance, don't forget to set virtualDisk object to null and update vmdk path accordingly.
	 * If this volume is not attached, this method does nothing.
	 * If this volume is a main volume, can't detach this volume.
	 */
	public void detachVolume() {
		// TODO : Detach volume method.
	}
	
	/**
	 * Migrate a volume from one datastore to another.
	 */
	public void MigrateVolumeOnAnotherDatastore() {
		// TODO : migrate a volume from origin ds to destination ds.
	}
	/**
	 * Migrate a volume from one instance vm to another instance vm.
	 */
	public void MigrateVolumeInstanceToAnoter() {
		// TODO : migrate a volume from origin instance to destination instance.
	}
	

	/**
	 * Create a directory for the volume management.
	 * @param dc
	 * @param ds
	 * @param destFolder
	 * @return
	 * @throws IOException
	 */
    private static boolean mkdir(final Datacenter dc, final Datastore ds, String destFolder) throws IOException {
        String dsName = ds.getName();
        destFolder = "[" + dsName + "]" + destFolder;
        
        FileManager fileManager = VCenterClient.getServiceInstance().getFileManager();
        
        if (fileManager == null) {
        	LOGGER.warn("File manager is not available on this vcenter server !");
        	LOGGER.warn("Cant create the directory " + destFolder + " on datastore : " + dsName);
        	return false;
        }
        
        if (!exists(destFolder, ds)) {
            LOGGER.info("Creating directory : " + destFolder);
            fileManager.makeDirectory(destFolder, dc, true);
            return true;
        }
        LOGGER.info("Directory : " + destFolder + " could not be created because it already exists");
        return false;
    }
	
    /**
	 * Find a volume full path for the volume name in datastore.
	 * @param ds
	 * @param volumeName (ex: data1)
	 * @return the full path like "[datastore1] /datavm1/data1.vmdk", may return null if no volume is found.
	 */
	private String findVolumeVMDKPathForName() {
		String fullPath = null;
		String dsName = ds.getName();
		String basePath = null;
		
		VmDiskFileQueryFilter vdiskFilter = new VmDiskFileQueryFilter();
        vdiskFilter.setControllerType(new String[]{ controllerType }); // default is SCSI.
        
        VmDiskFileQuery fQuery = new VmDiskFileQuery();
        fQuery.setFilter(vdiskFilter);
        
        HostDatastoreBrowserSearchSpec searchSpec = new HostDatastoreBrowserSearchSpec();
        searchSpec.setQuery(new FileQuery[]{fQuery});
        searchSpec.setMatchPattern(new String[] {volumeName + ".vmdk"}); // ".*"
        
        FileQueryFlags fqf = new FileQueryFlags();
        fqf.setFileSize(true);
        fqf.setModification(true);
        fqf.setFileOwner(true);
        fqf.setFileType(true);
        
        searchSpec.setDetails(fqf);
        try {
        	Task subFolderTask = ds.getBrowser().searchDatastoreSubFolders_Task("[" + dsName + "]", searchSpec);
        	subFolderTask.waitForTask();
            TaskInfo tInfo = subFolderTask.getTaskInfo();
            ArrayOfHostDatastoreBrowserSearchResults searchResult
                    = (ArrayOfHostDatastoreBrowserSearchResults) tInfo.getResult();
            HostDatastoreBrowserSearchResults[] results = null;
            if (searchResult == null) {
                return null;
            }
            results = searchResult.getHostDatastoreBrowserSearchResults();
            
            if (results == null) {
                return null;
            }
            int len = searchResult.getHostDatastoreBrowserSearchResults().length;
            
            for (int j = 0; j < len; j++) {
                HostDatastoreBrowserSearchResults sres = searchResult.HostDatastoreBrowserSearchResults[j];
                basePath = sres.getFolderPath();
                FileInfo[] fileArray = sres.getFile();
                if (fileArray == null) {
                    continue;
                }
                
                for (FileInfo fileInfo : fileArray) {
                    fullPath = basePath + fileInfo.getPath();
                    size = fileInfo.getFileSize().floatValue() / (1024 * 1024); // This give the real size of the vmdk not the global capacity.
                    modifiedDate = fileInfo.getModification();
                    break;
                }
            }
            
        } catch (RemoteException | InterruptedException ex) {
        	LOGGER.error("Cannot find the volume : " + volumeName + " --< message: " + ex.getMessage());
        	ex.printStackTrace();
        }
        
		return fullPath;
	}
	
	/**
     * Utility method, check if a folder exist or a path on the datastore.
     * @param path
     * @param ds
     * @return
     * @throws IOException
     */
    private static boolean exists(String path, Datastore ds) {
        //works for both files and folders

        path = "[" + ds.getName() + "]" + path;
        
        HostDatastoreBrowser hdb = ds.getBrowser();

        String[] splitPath = path.split("/");
        String fileName = splitPath[splitPath.length - 1];
        String folder = path.substring(0, path.length() - fileName.length());
        HostDatastoreBrowserSearchSpec fileSearchSpec = new HostDatastoreBrowserSearchSpec();
        fileSearchSpec.setMatchPattern(new String[]{fileName});

        try {
            Task task = hdb.searchDatastore_Task(folder, fileSearchSpec);
            task.waitForTask();
            HostDatastoreBrowserSearchResults searchResults =
                    (HostDatastoreBrowserSearchResults) task.getTaskInfo().getResult();
            if (searchResults == null) {
                return false;
            }
            FileInfo[] fileInfo = searchResults.getFile();
            return (fileInfo != null && fileInfo.length > 0);
        } catch (com.vmware.vim25.FileNotFound ex) {
            //normal case
        } catch (Exception ex) {
            LOGGER.error("Exception while testing if " + path + " exists ", ex);
        }

        return false;
    }
    
   // Getters and setters. 
    
	public String getVolumeName() {
		return volumeName;
	}
	public void setVolumeName(String volumeName) {
		this.volumeName = volumeName;
	}
	public Float getSize() {
		return size;
	}
	public void setSize(Float size) {
		this.size = size;
	}
	public String getFullPath() {
		return fullPath;
	}
	public void setFullPath(String fullPath) {
		this.fullPath = fullPath;
	}
	public String getMountPoint() {
		return mountPoint;
	}
	public void setMountPoint(String mountPoint) {
		this.mountPoint = mountPoint;
	}
	public boolean isMainVolume() {
		return mainVolume;
	}
	public void setMainVolume(boolean mainVolume) {
		this.mainVolume = mainVolume;
	}
	public String getVolumeState() {
		return volumeState;
	}
	public void setVolumeState(String volumeState) {
		this.volumeState = volumeState;
	}
	public boolean isExist() {
		return exist;
	}
	public void setExist(boolean exist) {
		this.exist = exist;
	}
	public boolean isAttached() {
		return attached;
	}
	public void setAttached(boolean attached) {
		this.attached = attached;
	}
	public String getVmName() {
		return vmName;
	}
	public void setVmName(String vmName) {
		this.vmName = vmName;
	}
	public Calendar getModifiedDate() {
		return modifiedDate;
	}
	public void setModifiedDate(Calendar modifiedDate) {
		this.modifiedDate = modifiedDate;
	}
	
    
    
    
}
