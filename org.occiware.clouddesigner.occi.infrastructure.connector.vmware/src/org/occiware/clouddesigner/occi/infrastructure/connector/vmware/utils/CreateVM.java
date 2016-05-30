package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils;

import java.rmi.RemoteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.ResourcePool;

public class CreateVM {

	private static Logger LOGGER = LoggerFactory.getLogger(CreateVM.class);

	private final VirtualMachineConfigSpec vmSpec;

	private final ResourcePool rp;

	private final HostSystem host;

	private final Folder vmFolder;

	public CreateVM(VirtualMachineConfigSpec vmSpec, ResourcePool rp, HostSystem host, Folder vmFolder) {
		super();
		this.vmSpec = vmSpec;
		this.rp = rp;
		this.host = host;
		this.vmFolder = vmFolder;
	}

	/**
	 * Create a task.
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
					com.vmware.vim25.mo.Task taskVm = vmFolder.createVM_Task(vmSpec, rp, host);

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
