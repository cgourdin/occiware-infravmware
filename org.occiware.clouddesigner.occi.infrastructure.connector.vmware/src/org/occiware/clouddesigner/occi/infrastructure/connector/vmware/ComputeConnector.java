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
 *
 * Generated at Tue May 10 13:08:38 CEST 2016 from platform:/plugin/org.occiware.clouddesigner.occi.infrastructure/model/Infrastructure.occie by org.occiware.clouddesigner.occi.gen.connector
 */
package org.occiware.clouddesigner.occi.infrastructure.connector.vmware;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.occiware.clouddesigner.occi.AttributeState;
import org.occiware.clouddesigner.occi.Link;
import org.occiware.clouddesigner.occi.infrastructure.ComputeStatus;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.allocator.AllocatorImpl;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.ClusterHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.DatacenterHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.DatastoreHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.HostHelper;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VCenterClient;
import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils.VMHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachineFileInfo;
import com.vmware.vim25.VirtualMachineGuestOsIdentifier;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

/**
 * Connector implementation for the OCCI kind: - scheme:
 * http://schemas.ogf.org/occi/infrastructure# - term: compute - title: Compute
 * Resource
 */
public class ComputeConnector extends org.occiware.clouddesigner.occi.infrastructure.impl.ComputeImpl {
	/**
	 * Initialize the logger.
	 */
	private static Logger LOGGER = LoggerFactory.getLogger(ComputeConnector.class);

	/**
	 * Define VMWare specifications for this compute.
	 */
	protected VirtualMachineConfigSpec vmSpec = null;

	public static final String VIRTUAL_MACHINE = "VirtualMachine";

	private String datacenterName = null;
	private String datastoreName = null;
	private String clusterName = null;
	/**
	 * Represent the physical compute which be used for host this virtual
	 * machine.
	 */
	private String hostSystemName = null;

	/**
	 * Constructs a compute connector.
	 */
	ComputeConnector() {
		LOGGER.debug("Constructor called on " + this);

	}

	//
	// OCCI CRUD callback operations.
	//

	/**
	 * Called when this Compute instance is completely created.
	 */
	@Override
	public void occiCreate() {
		LOGGER.debug("occiCreate() called on " + this);
		if (!checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		ServiceInstance si = VCenterClient.getServiceInstance();
		Folder rootFolder = si.getRootFolder();
		AllocatorImpl allocator = new AllocatorImpl(rootFolder);
		boolean toCreate = false;
		VirtualMachine vm = null;
		String vmName = this.getTitle();
		HostSystem host = null;
		ClusterComputeResource cluster = null;
		/**
		 * Network name (host / hardware).
		 */
		String netName = null;
		/**
		 * Network adapter name. ex: Network Adapter 1.
		 */
		String nicName = null;

		// Get image via OS_TPL mixin, for now this is attributes of this
		// object, and will be on an object mixin in future.

		String osMixinTerm;
		String guestOsId = null;

		// For now, we set manually attributes for datacenterName, datastoreName
		// and clusterName.
		for (AttributeState attrState : this.getAttributes()) {

			switch (attrState.getName()) {
			case "datacenterName":
				this.setDatacenterName(attrState.getName());
				break;
			case "clusterName":
				this.setClusterName(attrState.getName());
				break;
			case "hostSystemName":
				this.setHostSystemName(attrState.getName());
				break;
			}
		}

		// Datacenter part. first objects of the tree.
		Datacenter datacenter = DatacenterHelper.findDatacenterForName(rootFolder, this.getDatacenterName());
		if (datacenter == null) {
			// Allocate automaticly the datacenter, if no datacenter found, a
			// default datacenter will be created.
			datacenter = allocator.allocateDatacenter();
			if (datacenter == null) {
				LOGGER.error("Cant allocate a datacenter, cause : no available datacenter to allocate.");
				VCenterClient.disconnect();
				return;
			}
		}
		this.setDatacenterName(datacenter.getName());

		// Defines if vm is already setup in vcenter, if this is the case we
		// retrieve its values.
		try {
			toCreate = !VMHelper.isVMExistForName(datacenter.getVmFolder(), vmName);
		} catch (RemoteException ex) {
			LOGGER.error("cant query to check if the virtual machine exist, msg: " + ex.getMessage());
			VCenterClient.disconnect();
			return;
		}
		if (toCreate) {

			// Cluster part (not mandatory), we could create a VM (host before)
			// on datacenter
			// directly.

			cluster = ClusterHelper.findClusterForName(datacenter, this.getClusterName());
			if (cluster == null) {
				// if no cluster is present and this cluster has no name, we
				// can't create a new cluster if there's a name, we create a
				// cluster.
				if (this.getClusterName() != null) {
					// create a new cluster with this name
					// TODO : in future mixin with attributes pass here.
					cluster = ClusterHelper.createCluster(datacenter, this.getClusterName());
				} else {
					// Assign a cluster that already exist, if none found, no
					// cluster.
					cluster = allocator.allocateCluster();
				}

			}
			if (cluster == null) {
				LOGGER.error(
						"cant allocate a cluster --< No cluster available on datacenter : " + datacenter.getName());
				VCenterClient.disconnect();
				return;
			} else {
				this.setClusterName(cluster.getName());
			}

			// Image part.
			VirtualMachine vmTemplate = null;
			// identifier of the os guest or the template image
			// osMixinTerm = this.getOsTpl(); // Get value of os_tpl attribute
			// (in
			// near futur this will be mixin
			// object).
			// TODO : Mixin os_tpl --> term value is not accessible via
			// clouddesigner for now, when there is a solution, this will be
			// replaced by the good place.
			osMixinTerm = this.getSummary();
			if (osMixinTerm != null && !osMixinTerm.trim().isEmpty()) {
				// Search the OS template (VM Template) for this name.
				try {
					vmTemplate = VMHelper.findVMForName(datacenter.getVmFolder(), osMixinTerm);
				} catch (RemoteException ex) {
					LOGGER.error("Error while searching the vm template folder.");
					LOGGER.error("Message: " + ex.getMessage());
					return;
				}
				if (vmTemplate == null) {
					LOGGER.warn("No virtual machine template found with this name : " + osMixinTerm);
					// No VM template found, retrograde to guestOSId.
					// Get the corresponding value from api :
					guestOsId = VirtualMachineGuestOsIdentifier.valueOf(osMixinTerm).toString();
				} else {
					if (vmTemplate.getConfig().isTemplate()) {
						guestOsId = vmTemplate.getConfig().getGuestId();
					}
				}
			}

			// Detect the hostsystem for deploying this virtual machine on.
			try {
				host = HostHelper.findHostSystemForName(datacenter.getHostFolder(), this.getHostSystemName());
			} catch (RemoteException ex) {
				LOGGER.error("Error while searching host folder.");
				LOGGER.error("Message: " + ex.getMessage());
				return;
			}
			if (host == null) {
				if (this.getHostSystemName() == null) {
					host = allocator.allocateHostSystem();
					if (host == null) {
						LOGGER.error(
								"cant allocate automaticly an hostsystem, cause: there's no available host on the datacenter: "
										+ datacenter.getName());
						VCenterClient.disconnect();
						return;
					} else {
						this.setHostSystemName(host.getName());
					}
				} else {
					// Error on allocating the hostsystem.
					LOGGER.error("cant allocate the hostSystem: " + this.getHostSystemName()
							+ " --< cause: this doesnt exist on the datacenter: " + datacenter.getName());
					VCenterClient.disconnect();
					return;
				}
			}
			this.setHostSystemName(host.getName());

			// Get the devices storage.
			// The storage is based on a datastore, we must find the
			// corresponding datastore.

			// First we search the datastoreName on entity link.
			EList<Link> links = this.getLinks();
			Datastore datastore = null;
			StoragelinkConnector stMain = getMainStorageLink();
			List<StoragelinkConnector> stOtherLinks = getOtherStorageLink();

			if (!links.isEmpty()) {
				// Searching on main storageLink attributes.

				if (stMain != null) {
					for (AttributeState attrState : stMain.getAttributes()) {
						if (attrState.getName().equals("datastoreName")) {
							this.setDatastoreName(attrState.getValue());
							break;
						}
					}

					if (this.getDatastoreName() != null) {
						datastore = DatastoreHelper.findDatastoreForName(datacenter, this.getDatastoreName());
					}
				}
			}
			// TODO : Add multiple mounted disks support.
			if (!stOtherLinks.isEmpty()) {
				LOGGER.warn(
						"Warning other disks detected for this compute instance, this is not supported at this time, and may be supported in near future");
			}
			// Get Main disk.
			if (stMain == null && vmTemplate == null) {
				LOGGER.error("No main disk storage defined on / or on c:");
				VCenterClient.disconnect();
				return;
			}
			StorageConnector mainStorage = null;
			if (vmTemplate == null && stMain.getSource() instanceof StorageConnector) {
				mainStorage = (StorageConnector) stMain.getSource();
			} else if (vmTemplate == null) {
				mainStorage = (StorageConnector) stMain.getTarget();
			}

			// Datastore automatic allocation if none found.
			if (datastore == null) {
				// Allocate a datastore automaticly.
				datastore = allocator.allocateDatastore();
				if (datastore == null) {
					LOGGER.error("cant allocate a datastore on datacenter: " + datacenter.getName()
							+ " --> there's no available datastore on the datacenter.");
					VCenterClient.disconnect();
					return;
				}
			}
			this.setDatastoreName(datastore.getName());

			Folder vmFolder;
			Task task = null;
			if (vmTemplate != null) {
				// We clone the vm.
				try {
					vmFolder = (Folder) datacenter.getVmFolder();
					ResourcePool rp = (ResourcePool) new InventoryNavigator(datacenter)
							.searchManagedEntities("ResourcePool")[0];
					VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
					VirtualMachineRelocateSpec vmRelocate = new VirtualMachineRelocateSpec();
					vmRelocate.setHost(host.getMOR());
					vmRelocate.setPool(rp.getMOR());
					vmRelocate.setDatastore(datastore.getMOR());
					cloneSpec.setLocation(vmRelocate);
					cloneSpec.setTemplate(false);
					cloneSpec.setPowerOn(false);
					if (vmTemplate.getCurrentSnapShot() != null) {
						cloneSpec.snapshot = vmTemplate.getCurrentSnapShot().getMOR();
					}

					task = vmTemplate.cloneVM_Task(vmFolder, vmName, cloneSpec);
					LOGGER.info("Creating the Virtual Machine >> " + this.getTitle() + " << from template: "
							+ vmTemplate.getName());
					// try {
					// task.waitForTask();
					//
					// // String status = task.waitForTask(2000, 2000);
					//// if (status == Task.SUCCESS) {
					//// // TODO : Observer change status value.
					//// LOGGER.info("VM created successfully.");
					//// } else {
					//// LOGGER.error("VM was not created or has error, please
					// check your configuration.");
					//// }
					// } catch (InterruptedException ex) {
					// ex.printStackTrace();
					// VCenterClient.disconnect();
					// return;
					// }

				} catch (RemoteException ex) {
					LOGGER.error("VM was not created or has errors, please check your vcenter and your configuration");
					LOGGER.error("Message: " + ex.getMessage());
					if (ex.getMessage() == null) {
						ex.printStackTrace();
					}
					VCenterClient.disconnect();
					return;
				}
			} else {
				try {
					// No vm template defined, building a new vm from scratch,
					// there
					// is no os system installed on..
					LOGGER.info("Creating the Virtual Machine from scratch : " + vmName);
					int cKey = 1000;

					String diskMode = null;
					for (AttributeState attrState : mainStorage.getAttributes()) {
						if (attrState.getName().equalsIgnoreCase("diskmode")) {
							diskMode = attrState.getValue();
						}
					}
					if (diskMode == null) {
						// mode: persistent|independent_persistent,
						// independent_nonpersistent
						LOGGER.warn("Default diskmode setted to persistent");
						diskMode = "persistent";
					}

					// Disk size in kiloBytes.
					Float diskSize = mainStorage.getSize();
					Long diskSizeGB = diskSize.longValue();
					if (diskSizeGB == 0L) {
						LOGGER.error("The main disk size must be > 0 in GigaBytes");
						VCenterClient.disconnect();
						return;
					}
					Long diskSizeKB = diskSizeGB * 1024 * 1024;
					VirtualDeviceConfigSpec scsiSpec = VMHelper.createScsiSpec(cKey);
					VirtualDeviceConfigSpec diskSpec = VMHelper.createDiskSpec(this.getDatastoreName(), cKey,
							diskSizeKB, diskMode);

					// Network part : VM Network.
					// We use predefined network interface (from host).

					// Get the virtual network interface name.
					NetworkConnector netConnector = null;
					List<NetworkinterfaceConnector> networkinterfaces = this.getNetworkInterfaceConnectors();
					if (networkinterfaces.isEmpty()) {
						// Searching an existing network device on host.
						LOGGER.info(
								"No network interfaces defined, searching for a network on host : " + host.getName());

						Network network = allocator.allocateNetwork();
						if (network == null) {
							LOGGER.error(
									"No host networks is available for this virtual machine, please setup a new network in vcenter.");
							VCenterClient.disconnect();
							return;
						}
						netName = network.getName();
					} else {
						// Get the name netinterface.
						for (NetworkinterfaceConnector netInt : networkinterfaces) {
							netName = netInt.getTitle();
							if (netInt.getSource() instanceof NetworkConnector) {
								netConnector = (NetworkConnector) netInt.getSource();
							} else {
								netConnector = (NetworkConnector) netInt.getTarget();
							}
							break;
						}
					}
					// if (netConnector == null) {
					// LOGGER.error("You must setup a virtual network");
					// VCenterClient.disconnect();
					// return;
					// }
					//
					// if (netName == null) {
					// LOGGER.error("You must set a network interface name");
					// VCenterClient.disconnect();
					// return;
					// }

					// Get the virtual adapter network name.
					if (netConnector != null) {
						nicName = netConnector.getTitle();
					} else {
						// Default virtual network name.
						nicName = "virtual network";
					}

					// TODO : Check VMWare tools, if no vmware tools, the sdk
					// will
					// not
					// give the ipv4, nor ipv6 .
					// + upgrade automaticly VMWare Tools via VIJava.

					// Network configuration.
					VirtualDeviceConfigSpec nicSpec = VMHelper.createNicSpec(netName, nicName);

					// if no guest os Id and no template, assume that is an
					// empty vm
					// with otherGuest term.
					if (guestOsId == null) {
						// No guest os defined nor template on creation.
						// Setting default to : otherGuest.
						guestOsId = VirtualMachineGuestOsIdentifier.otherGuest.toString();
					}

					// Define the vmSpec configuration object.
					vmSpec = new VirtualMachineConfigSpec();

					vmSpec.setName(vmName);
					vmSpec.setAnnotation("VirtualMachine Annotation");

					Float memSizeGB = this.getMemory();

					Long memSizeGBLng = memSizeGB.longValue();
					Long memSizeMB = memSizeGBLng * 1024;

					if (memSizeGBLng == 0L || this.getCores() == 0) {
						LOGGER.error("You must set the memory size (in GB) and the number of cores.");
						VCenterClient.disconnect();
						return;
					}

					vmSpec.setMemoryMB(memSizeMB);
					vmSpec.setNumCPUs(this.getCores());
					vmSpec.setGuestId(guestOsId);
					vmSpec.setCpuHotAddEnabled(true);
					vmSpec.setCpuHotRemoveEnabled(true);

					vmSpec.setDeviceChange(new VirtualDeviceConfigSpec[] { scsiSpec, diskSpec, nicSpec });
					// Create vm file info for vmx file.
					VirtualMachineFileInfo vmfi = new VirtualMachineFileInfo();
					vmfi.setVmPathName("[" + datastoreName + "]");
					vmSpec.setFiles(vmfi);

					ResourcePool rp = (ResourcePool) new InventoryNavigator(datacenter)
							.searchManagedEntities("ResourcePool")[0];

					vmFolder = datacenter.getVmFolder();

					// Create effectively the vm on folder.
					task = vmFolder.createVM_Task(vmSpec, rp, host);
					// Create vm terminated

				} catch (RemoteException ex) {
					LOGGER.error("Cannot create the virtual machine : " + ex.getMessage());
					VCenterClient.disconnect();
					return;
				}

			} // endif vmTemplate exist.
			
			final Task customThreadedTask = task; 
			// Execute in a new thread.
			Thread thread = new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						String result = customThreadedTask.waitForTask();
						if (result == Task.SUCCESS) {
							LOGGER.info("Virtual Machine successfully created !");
							// Find the values of this vm and update this
							// compute resource model.
							occiRetrieve();

						} else {
							LOGGER.info("VM couldn't be created !");

						}
					} catch (RemoteException | InterruptedException ex) {
						ex.printStackTrace();
					} finally {
						VCenterClient.disconnect();
					}

				}
			});

			thread.start();

			// if (vmFolder != null) {
			// vm = VMHelper.findVMForName(vmFolder, vmName);
			// if (vm != null) {
			// VMHelper.mountGuestVmTools((Folder) vm.getParent(),
			// this.getTitle());
			// // assign hot config enabled (default).
			// VMHelper.hotReconfigEnable((Folder) vm.getParent(),
			// this.getTitle(), true);
			// }
			// }

		} // Endif toCreate.

		// In all case invoke a disconnect from vcenter.
		VCenterClient.disconnect();
	}

	/**
	 * Called when this Compute instance must be retrieved.
	 */
	@Override
	public void occiRetrieve() {

		LOGGER.debug("occiRetrieve() called on " + this);
		if (!checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		// TODO: Implement this callback or remove this method.

		// In the end we disconnect.
		VCenterClient.disconnect();
	}

	/**
	 * Called when this Compute instance is completely updated.
	 */
	@Override
	public void occiUpdate() {
		LOGGER.debug("occiUpdate() called on " + this);
		if (!checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		// TODO: Implement this callback or remove this method.

		// In the end we disconnect.
		VCenterClient.disconnect();
	}

	/**
	 * Called when this Compute instance will be deleted.
	 */
	@Override
	public void occiDelete() {
		LOGGER.debug("occiDelete() called on " + this);
		if (!checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		// TODO: Implement this callback or remove this method.

		// In the end we disconnect.
		VCenterClient.disconnect();
	}

	//
	// Compute actions.
	//

	/**
	 * Implement OCCI action: - scheme:
	 * http://schemas.ogf.org/occi/infrastructure/compute/action# - term: start
	 * - title: Start the system
	 */
	@Override
	public void start() {
		LOGGER.debug("Action start() called on " + this);
		if (!checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		// Compute State Machine.
		switch (getState().getValue()) {

		case ComputeStatus.ACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=active, action=\"start\")...");

			// TODO Implement transition(state=active, action="start")

			break;

		case ComputeStatus.INACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=inactive, action=\"start\")...");

			// TODO Implement transition(state=inactive, action="start")

			break;

		case ComputeStatus.SUSPENDED_VALUE:
			LOGGER.debug("Fire transition(state=suspended, action=\"start\")...");

			// TODO Implement transition(state=suspended, action="start")

			break;

		case ComputeStatus.ERROR_VALUE:
			LOGGER.debug("Fire transition(state=error, action=\"start\")...");

			// TODO Implement transition(state=error, action="start")

			break;

		default:
			break;
		}

		// In the end we disconnect.
		VCenterClient.disconnect();
	}

	/**
	 * Implement OCCI action: - scheme:
	 * http://schemas.ogf.org/occi/infrastructure/compute/action# - term: stop -
	 * title: Stop the system (graceful, acpioff or poweroff)
	 */
	@Override
	public void stop(final org.occiware.clouddesigner.occi.infrastructure.StopMethod method) {
		LOGGER.debug("Action stop(" + "method=" + method + ") called on " + this);
		if (!checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		// Compute State Machine.
		switch (getState().getValue()) {

		case ComputeStatus.ACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=active, action=\"stop\")...");

			// TODO Implement transition(state=active, action="stop")

			break;

		case ComputeStatus.INACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=inactive, action=\"stop\")...");

			// TODO Implement transition(state=inactive, action="stop")

			break;

		case ComputeStatus.SUSPENDED_VALUE:
			LOGGER.debug("Fire transition(state=suspended, action=\"stop\")...");

			// TODO Implement transition(state=suspended, action="stop")

			break;

		case ComputeStatus.ERROR_VALUE:
			LOGGER.debug("Fire transition(state=error, action=\"stop\")...");

			// TODO Implement transition(state=error, action="stop")

			break;

		default:
			break;
		}

		// In the end we disconnect.
		VCenterClient.disconnect();
	}

	/**
	 * Implement OCCI action: - scheme:
	 * http://schemas.ogf.org/occi/infrastructure/compute/action# - term:
	 * restart - title: Restart the system (graceful, warm or cold)
	 */
	@Override
	public void restart(final org.occiware.clouddesigner.occi.infrastructure.RestartMethod method) {
		LOGGER.debug("Action restart(" + "method=" + method + ") called on " + this);
		if (!checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		// Compute State Machine.
		switch (getState().getValue()) {

		case ComputeStatus.ACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=active, action=\"restart\")...");

			// TODO Implement transition(state=active, action="restart")

			break;

		case ComputeStatus.INACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=inactive, action=\"restart\")...");

			// TODO Implement transition(state=inactive, action="restart")

			break;

		case ComputeStatus.SUSPENDED_VALUE:
			LOGGER.debug("Fire transition(state=suspended, action=\"restart\")...");

			// TODO Implement transition(state=suspended, action="restart")

			break;

		case ComputeStatus.ERROR_VALUE:
			LOGGER.debug("Fire transition(state=error, action=\"restart\")...");

			// TODO Implement transition(state=error, action="restart")

			break;

		default:
			break;
		}

		// In the end we disconnect.
		VCenterClient.disconnect();
	}

	/**
	 * Implement OCCI action: - scheme:
	 * http://schemas.ogf.org/occi/infrastructure/compute/action# - term:
	 * suspend - title: Suspend the system (hibernate or in RAM)
	 */
	@Override
	public void suspend(final org.occiware.clouddesigner.occi.infrastructure.SuspendMethod method) {
		LOGGER.debug("Action suspend(" + "method=" + method + ") called on " + this);
		if (!checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		// Compute State Machine.
		switch (getState().getValue()) {

		case ComputeStatus.ACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=active, action=\"suspend\")...");

			// TODO Implement transition(state=active, action="suspend")

			break;

		case ComputeStatus.INACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=inactive, action=\"suspend\")...");

			// TODO Implement transition(state=inactive, action="suspend")

			break;

		case ComputeStatus.SUSPENDED_VALUE:
			LOGGER.debug("Fire transition(state=suspended, action=\"suspend\")...");

			// TODO Implement transition(state=suspended, action="suspend")

			break;

		case ComputeStatus.ERROR_VALUE:
			LOGGER.debug("Fire transition(state=error, action=\"suspend\")...");

			// TODO Implement transition(state=error, action="suspend")

			break;

		default:
			break;
		}

		// In the end we disconnect.
		VCenterClient.disconnect();
	}

	/**
	 * Implement OCCI action: - scheme:
	 * http://schemas.ogf.org/occi/infrastructure/compute/action# - term: save -
	 * title: Save the system (hot, deferred)
	 */
	@Override
	public void save(final org.occiware.clouddesigner.occi.infrastructure.SaveMethod method,
			final java.lang.String name) {
		LOGGER.debug("Action save(" + "method=" + method + "name=" + name + ") called on " + this);
		if (!checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		// Compute State Machine.
		switch (getState().getValue()) {

		case ComputeStatus.ACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=active, action=\"save\")...");

			// TODO Implement transition(state=active, action="save")

			break;

		case ComputeStatus.INACTIVE_VALUE:
			LOGGER.debug("Fire transition(state=inactive, action=\"save\")...");

			// TODO Implement transition(state=inactive, action="save")

			break;

		case ComputeStatus.SUSPENDED_VALUE:
			LOGGER.debug("Fire transition(state=suspended, action=\"save\")...");

			// TODO Implement transition(state=suspended, action="save")

			break;

		case ComputeStatus.ERROR_VALUE:
			LOGGER.debug("Fire transition(state=error, action=\"save\")...");

			// TODO Implement transition(state=error, action="save")

			break;

		default:
			break;
		}

		// In the end we disconnect.
		VCenterClient.disconnect();
	}

	// TODO : All these methods are to use with mixin in near future.

	/**
	 * Usage with Mixin in future.
	 * 
	 * @return
	 */
	public String getDatacenterName() {
		if (datacenterName == null) {
			// TODO : Search with mixin (or resources, depends on the
			// infrastructure extension choice).

		}

		return datacenterName;
	}

	/**
	 * Usage with Mixin in future.
	 * 
	 * @return
	 */
	public void setDatacenterName(String datacenterName) {
		this.datacenterName = datacenterName;
	}

	/**
	 * Usage with mixin in future.
	 * 
	 * @return
	 */
	public String getDatastoreName() {
		return datastoreName;
	}

	/**
	 * Usage with Mixin in future.
	 * 
	 * @return
	 */
	public void setDatastoreName(String datastoreName) {
		this.datastoreName = datastoreName;
	}

	/**
	 * Usage with Mixin in future. Must have attributes (a lot...)
	 * 
	 * @return
	 */
	public String getClusterName() {
		return clusterName;
	}

	/**
	 * Usage with Mixin in future.
	 * 
	 * @return
	 */
	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}

	public String getHostSystemName() {
		return hostSystemName;
	}

	public void setHostSystemName(String hostSystemName) {
		this.hostSystemName = hostSystemName;
	}

	// /**
	// * Get from attributes OS_TPL and return value.
	// *
	// * @return
	// */
	// private String getOsTpl() {
	// String osTpl = null;
	// for (AttributeState attrState : this.getAttributes()) {
	// if (attrState.getName().equalsIgnoreCase("os_tpl")) {
	// osTpl = attrState.getValue();
	// break;
	// }
	// }
	//
	// return osTpl;
	// }

	/**
	 * if vcenter client connection is not set, this method will connect to
	 * vcenter.
	 */
	private boolean checkConnection() {
		if (!VCenterClient.isConnected()) {
			try {
				VCenterClient.init();
				VCenterClient.connect();
				return true;
			} catch (IOException ex) {
				LOGGER.error(ex.getMessage());
				return false;
			}
		} else {
			return true;
		}

	}

	/**
	 * Get Main storage link (link on main disk).
	 * 
	 * @return if a main storage is present return the main storagelink, if none
	 *         null value is returned.
	 */
	private StoragelinkConnector getMainStorageLink() {
		EList<Link> links = this.getLinks();
		List<StoragelinkConnector> storageLinks = new ArrayList<>();
		StoragelinkConnector mainStorageLink = null;
		for (Link link : links) {
			if (link instanceof StoragelinkConnector) {
				storageLinks.add((StoragelinkConnector) link);
			}
		}
		int storageLinkSize = storageLinks.size();

		// Detect where's the main disk.
		for (StoragelinkConnector stLink : storageLinks) {
			if (storageLinkSize == 1 || (stLink.getMountpoint() != null && (stLink.getMountpoint().equals("/")
					|| stLink.getMountpoint().startsWith("C:") || stLink.getMountpoint().startsWith("c:")))) {
				mainStorageLink = stLink;
				break;
			}
		}

		return mainStorageLink;
	}

	/**
	 * Get the other storageLinks, if none empty list is returned.
	 * 
	 * @return a list of storageLinkConnector if other storagelink found (not
	 *         the main storageLink).
	 */
	private List<StoragelinkConnector> getOtherStorageLink() {
		EList<Link> links = this.getLinks();
		List<StoragelinkConnector> storageLinks = new ArrayList<>();
		StoragelinkConnector stMain = getMainStorageLink();
		StoragelinkConnector stOther = null;
		for (Link link : links) {
			if (link instanceof StoragelinkConnector && stMain != null) {
				stOther = (StoragelinkConnector) link;
				if (!stOther.equals(stMain)) {
					storageLinks.add(stOther);
				}
			}
		}
		return storageLinks;
	}

	/**
	 * We get here the network connector.
	 * 
	 * @return
	 */
	private List<NetworkinterfaceConnector> getNetworkInterfaceConnectors() {
		EList<Link> links = this.getLinks();
		List<NetworkinterfaceConnector> netInts = new ArrayList<>();
		NetworkinterfaceConnector netInt;
		for (Link link : links) {
			if (link instanceof NetworkinterfaceConnector) {
				netInt = (NetworkinterfaceConnector) link;
				netInts.add(netInt);
			}
		}
		return netInts;

	}

}