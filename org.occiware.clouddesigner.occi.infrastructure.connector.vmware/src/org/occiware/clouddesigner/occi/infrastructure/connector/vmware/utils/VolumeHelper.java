package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils;

import java.io.IOException;
import java.rmi.RemoteException;

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
	
	public static String VIRTUAL_DISK = "VirtualDisk";
	
	/**
	 * Find a volume for his name on the datastore.
	 * @param ds
	 * @param volumeName (ex: data1)
	 * @return the full path like "[datastore1] /datavm1/data1.vmdk", may return null if no volume is found.
	 */
	public static String findVolumeVMDKPathForName(final Datastore ds, final String volumeName) {
		String fullPath = null;
		String dsName = ds.getName();
		String basePath = null;
		
		VmDiskFileQueryFilter vdiskFilter = new VmDiskFileQueryFilter();
        vdiskFilter.setControllerType(new String[]{ "VirtualSCSIController"}); // "VirtualIDEController", "VirtualSATAController"
        
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
        	Task subFolderTask = ds.getBrowser().searchDatastoreSubFolders_Task("[" + ds.getName() + "]", searchSpec);
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
	 * Is the volume exist on vcenter ?
	 * @param dc
	 * @param ds
	 * @param volumeName 
	 * @return true if volume exist, else false.
	 */
	public static boolean isExistVolumeForName(final Datastore ds, final String volumeName) {
		// Search the volume on datastore.
		String fullPath = findVolumeVMDKPathForName(ds, volumeName);
		return fullPath != null; 
	}
	
	
	/**
	 * Create an empty volume with no attachment on vm.
	 * @param dc (Datacenter)
	 * @param ds (Datastore)
	 * @param volumeName
	 */
	public static void createVolume(final Datacenter dc, final Datastore ds, final String volumeName, final String volumeSize) {
		String dsName = ds.getName();
		String fullPath = findVolumeVMDKPathForName(ds, volumeName);
		
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
		TODO !!!
		
		
		
		
	}
	
	/**
	 * Create a directory for the volume management.
	 * @param dc
	 * @param ds
	 * @param destFolder
	 * @return
	 * @throws IOException
	 */
    public static boolean mkdir(final Datacenter dc, final Datastore ds, String destFolder) throws IOException {
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
     * Check if a folder exist or a path on the datastore.
     * @param path
     * @param ds
     * @return
     * @throws IOException
     */
    public static boolean exists(String path, Datastore ds) throws IOException {
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
	
}
