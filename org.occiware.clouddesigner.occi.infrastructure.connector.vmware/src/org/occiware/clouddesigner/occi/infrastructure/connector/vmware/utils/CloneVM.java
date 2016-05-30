package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils;

import java.rmi.RemoteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.mo.Folder;

import com.vmware.vim25.mo.VirtualMachine;

public class CloneVM {

	private static Logger LOGGER = LoggerFactory.getLogger(CloneVM.class);

	private final Folder folder;

	private final VirtualMachine vmToClone;

	private final String vmNameDest;

	private final VirtualMachineCloneSpec cloneSpec;

	public CloneVM(Folder folder, VirtualMachine vmToClone, String vmNameDest, VirtualMachineCloneSpec cloneSpec) {
		super();
		this.folder = folder;
		this.vmToClone = vmToClone;
		this.vmNameDest = vmNameDest;
		this.cloneSpec = cloneSpec;
	}

	/**
	 * 
	 * @return result.
	 */
	public Runnable createTask() {

		Runnable run = new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				
				
				try {
					VCenterClient.checkConnection();
					com.vmware.vim25.mo.Task taskVm = vmToClone.cloneVM_Task(folder, vmNameDest, cloneSpec);
					String result = taskVm.waitForTask();
					if (result == com.vmware.vim25.mo.Task.SUCCESS) {
						LOGGER.info("Virtual Machine successfully created !");
						// Find the values of this vm and update this
						// compute resource model.
						// occiRetrieve();
						
					} else {
						LOGGER.info("VM couldn't be created !");
					
					}
				} catch (RemoteException | InterruptedException ex) {
					ex.printStackTrace();
					
				} finally {
					VCenterClient.disconnect();
				}
			}
		};	
			
		return run;
	}

}
