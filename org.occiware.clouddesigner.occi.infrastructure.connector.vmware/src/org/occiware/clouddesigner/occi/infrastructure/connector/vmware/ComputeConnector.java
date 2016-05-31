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

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.occiware.clouddesigner.occi.AttributeState;
import org.occiware.clouddesigner.occi.Link;
import org.occiware.clouddesigner.occi.infrastructure.Architecture;
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
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
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

	private String vmOldName = null;

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
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		ServiceInstance si = VCenterClient.getServiceInstance();
		Folder rootFolder = si.getRootFolder();
		AllocatorImpl allocator = new AllocatorImpl(rootFolder);
		boolean toCreate = false;
		
		String vmName = this.getTitle();
		vmOldName = vmName;
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

					// TODO : guest hostname (lvl OS), when set, create a
					// customizationSpec.setDomain(hostname) for the
					// corresponding operating system. It is not implemented for
					// now.

					if (vmTemplate.getCurrentSnapShot() != null) {
						cloneSpec.snapshot = vmTemplate.getCurrentSnapShot().getMOR();
					}

					LOGGER.info("Creating the Virtual Machine >> " + this.getTitle() + " << from template: "
							+ vmTemplate.getName());

					com.vmware.vim25.mo.Task taskVm = vmTemplate.cloneVM_Task(vmFolder, vmName, cloneSpec);
					// TODO : Monitoring Task in other thread.
					String result = taskVm.waitForTask();
					if (result == com.vmware.vim25.mo.Task.SUCCESS) {
						LOGGER.info("Virtual Machine successfully created from template : " + vmTemplate.getName());
						// Find the values of this vm and update this
						// compute resource model.
						// occiRetrieve();
					} else {
						LOGGER.info("VM couldn't be created ! vm name: " + vmName + " from template: "
								+ vmTemplate.getName());
					}
					// final Task task = vmTemplate.cloneVM_Task(vmFolder,
					// vmName, cloneSpec);
					// CloneVM cloneVM = new CloneVM(vmFolder, vmTemplate,
					// vmName, cloneSpec);
					// Runnable run = cloneVM.createTask();
					// new Thread(run).start();

				} catch (RemoteException | InterruptedException ex) {
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
					// will not give the ipv4, nor ipv6 .
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
					// vmSpec.setCpuHotRemoveEnabled(true);
					vmSpec.setMemoryHotAddEnabled(true);

					vmSpec.setDeviceChange(new VirtualDeviceConfigSpec[] { scsiSpec, diskSpec, nicSpec });
					// Create vm file info for vmx file.
					VirtualMachineFileInfo vmfi = new VirtualMachineFileInfo();
					vmfi.setVmPathName("[" + datastoreName + "]");
					vmSpec.setFiles(vmfi);

					ResourcePool rp = (ResourcePool) new InventoryNavigator(datacenter)
							.searchManagedEntities("ResourcePool")[0];

					vmFolder = datacenter.getVmFolder();

					// Create effectively the vm on folder.
					com.vmware.vim25.mo.Task taskVm = vmFolder.createVM_Task(vmSpec, rp, host);
					// TODO : Monitoring task object in other thread. See :
					// http://benohead.com/vi-java-api-monitoring-task-completion/
					String result = taskVm.waitForTask();
					if (result == com.vmware.vim25.mo.Task.SUCCESS) {
						LOGGER.info("Virtual Machine successfully created !");
						// Find the values of this vm and update this
						// compute resource model.
						// occiRetrieve();
					} else {
						LOGGER.info("VM couldn't be created !");
					}

					// Create vm terminated

				} catch (RemoteException | InterruptedException ex) {
					LOGGER.error("Cannot create the virtual machine : " + ex.getMessage());
					ex.printStackTrace();
					VCenterClient.disconnect();
					return;
				}

			} // endif vmTemplate exist.

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
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			LOGGER.warn("No connection to Vcenter has been established.");
			return;
		}
		// Retrieve all informations about this compute.
		String vmName = this.getTitle();
		if (vmOldName == null) {
			vmOldName = vmName;
		}
		ServiceInstance si = VCenterClient.getServiceInstance();
		Folder rootFolder = si.getRootFolder();
		// Search for the vm object.
		VirtualMachine vm = VMHelper.findVMForName(rootFolder, vmName);
		if (vm == null) {
			// Check if the name has changed...
			if (!vmOldName.equals(vmName)) {
				// The title attribute has changed from the last use this may
				// cause to not find the vm on vcenter.
				vm = VMHelper.findVMForName(rootFolder, vmOldName);
				// if found we set the title on the old name.
				if (vm != null) {
					this.setTitle(vmOldName);
					vmName = vmOldName;
				} else {
					// no vm exist with this name.
					LOGGER.warn("This virtual machine doesnt exist anymore.");
					VCenterClient.disconnect();
					return;
				}
			} else {
				// no vm exist with this name.
				LOGGER.warn("This virtual machine doesnt exist anymore.");
				VCenterClient.disconnect();
				return;
			}
		}
		HostSystem host = VMHelper.findHostSystemForVM(rootFolder, vmName);
		if (host == null) {
			LOGGER.error("No host found for this vm : " + vmName);
			VCenterClient.disconnect();
			return;
		}

		Datacenter dc = null;
		ClusterComputeResource cluster = null;
		Datastore ds = null;

		// Search for the cluster and datacenter info. (if any, it is not
		// mandatory to have a cluster, so it is a simple information.
		ManagedEntity mEntity = host.getParent();
		while (mEntity != null) {

			if (mEntity instanceof Datacenter) {
				dc = (Datacenter) mEntity;
				// LOGGER.info("Datacenter : " + mEntity.getName() + " << " +
				// dc.getName());
			}
			// if (mEntity instanceof Folder) {
			// folder = (Folder)mEntity;
			// // LOGGER.info("Folder: " + mEntity.getName() + " << " +
			// folder.getName());
			// }
			if (mEntity instanceof ClusterComputeResource) {
				cluster = (ClusterComputeResource) mEntity;
				// LOGGER.info("Cluster: " + mEntity.getName() + " << " +
				// cluster.getName());
			}
			if (mEntity instanceof Datastore) {
				ds = (Datastore) mEntity;
				// LOGGER.info("Cluster: " + mEntity.getName() + " << " +
				// datastore.getName());
			}

			// folder = (Folder)folder.getParent();
			mEntity = mEntity.getParent();
		}
		if (dc == null) {
			LOGGER.warn("No datacenter found for this virtual machine: " + vm.getName());
		} else {
			this.setDatacenterName(dc.getName());
		}
		if (cluster == null) {
			LOGGER.warn("No cluster found for this virtual machine: " + vm.getName());
		} else {
			this.setClusterName(cluster.getName());
		}
		if (ds == null) {
			// There is another way to find the dsname.
			try {
				Datastore[] dss = vm.getDatastores();
				if (dss != null && dss.length > 0) {
					ds = dss[0];
				}
				if (ds != null) {
					this.setDatastoreName(ds.getName());
				}

			} catch (RemoteException ex) {
				LOGGER.error("Error while searching all datastores for this virtual machine: " + vm.getName());
				LOGGER.error("Message: " + ex.getMessage());
				VCenterClient.disconnect();
				return;
			}

		} else {
			this.setDatastoreName(ds.getName());
		}

		// Load the compute information from vCenter.
		Integer numCpu = VMHelper.getNumCPU(vm);
		Float memoryGB = VMHelper.getMemoryGB(vm);
		String architecture = VMHelper.getArchitecture(vm);
		Float speed = VMHelper.getCPUSpeed(vm);
		// Define the states of this vm.
		String vmState = VMHelper.getPowerState(vm);
		// String vmGuestState = VMHelper.getGuestState(vm);
		if (architecture.equals("x64")) {
			this.setArchitecture(Architecture.X64);
		} else {
			this.setArchitecture(Architecture.X86);
		}
		this.setCores(numCpu);
		this.setMemory(memoryGB);
		this.setSpeed(speed);
		this.setState(defineStatus(vmState));

		// In the end we disconnect.
		VCenterClient.disconnect();
	}

	/**
	 * Called when this Compute instance is completely updated.
	 */
	@Override
	public void occiUpdate() {
		LOGGER.debug("occiUpdate() called on " + this);
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		// Load the vm information.
		String vmName = this.getTitle();

		if (vmName == null) {
			LOGGER.error("The title must be set, as it is used as the VM name (unique).");
			VCenterClient.disconnect();
			return;
		}
		if (vmOldName == null) {
			vmOldName = vmName;
		}

		VirtualMachine vm = VMHelper.loadVirtualMachine(vmName);
		if (vm == null) {
			// The title may has been changed.
			if (!vmOldName.equals(vmName)) {
				// The title have been changed.
				// We load the vm with the old one.
				vm = VMHelper.loadVirtualMachine(vmOldName);
				if (vm != null) {
					LOGGER.info("The virtual machine name has been changed to a new one, updating...");
					VMHelper.renameVM(vm, vmName);
					vm = VMHelper.loadVirtualMachine(vmName);

				} else {
					VCenterClient.disconnect();
					return;
				}
			}

		}

		// Update config.
		try {
			VMHelper.reconfigureVm(vm, this.getCores(), this.getMemory());
		} catch (RemoteException ex) {
			LOGGER.error("Error while updating the virtual machine configuration : " + vmName + " message: "
					+ ex.getMessage());
			ex.printStackTrace();
		}

		// In the end we disconnect.
		VCenterClient.disconnect();
	}

	/**
	 * Called when this Compute instance will be deleted.
	 */
	@Override
	public void occiDelete() {
		LOGGER.debug("occiDelete() called on " + this);

		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		String vmName = this.getTitle();
		if (vmName == null) {
			LOGGER.error("The title must be set, as it is used as the VM name (unique).");
			VCenterClient.disconnect();
			return;
		}
		VirtualMachine vm = VMHelper.loadVirtualMachine(vmName);
		if (vm == null) {
			// Check if an old name exist.
			if (vmOldName != null && !vmOldName.equals(vmName)) {
				vm = VMHelper.loadVirtualMachine(vmOldName);
				if (vm == null) {
					VCenterClient.disconnect();
					return;
				}
			} else {
				VCenterClient.disconnect();
				return;
			}

		}
		VMHelper.destroyVM(vm);

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
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}

		String vmName = this.getTitle();
		if (vmName == null) {
			LOGGER.error("The title must be set, as it is used as the VM name (unique).");
			VCenterClient.disconnect();
			return;
		}
		VirtualMachine vm = VMHelper.loadVirtualMachine(vmName);
		if (vm == null) {
			VCenterClient.disconnect();
			return;
		}
		String vmPowerState = VMHelper.getPowerState(vm);

		if (vmPowerState.equals(VMHelper.POWER_ON)) {
			LOGGER.info("The virtual machine " + vmName + " is already started.");
		} else {
			// in the other case we start the compute.
			VMHelper.powerOn(vm);
		}

		// TODO : Reload vm with last values.
		this.setState(defineStatus(vmPowerState));

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
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}

		String vmName = this.getTitle();
		if (vmName == null) {
			LOGGER.error("The title must be set, as it is used as the VM name (unique).");
			VCenterClient.disconnect();
			return;
		}
		VirtualMachine vm = VMHelper.loadVirtualMachine(vmName);
		if (vm == null) {
			VCenterClient.disconnect();
			return;
		}
		String vmPowerState = VMHelper.getPowerState(vm);
		if (vmPowerState.equals(VMHelper.POWER_OFF)) {
			LOGGER.info("The virtual machine " + vmName + " is already stopped.");
		} else {
			// in the other case we start the compute.
			// if (graceful) shutdown guest os and poweroff.
			// if acpioff ??
			// if poweroff direct poweroff.
			switch (method) {
			case GRACEFUL:
				VMHelper.graceFulPowerOff(vm);
				break;
			case POWEROFF:
				VMHelper.powerOff(vm);
				break;
			case ACPIOFF:
				VMHelper.powerOff(vm);
				break;
			}
		}
		// TODO : Reload vm object with last values.
		this.setState(defineStatus(vmPowerState));

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
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}

		String vmName = this.getTitle();
		if (vmName == null) {
			LOGGER.error("The title must be set, as it is used as the VM name (unique).");
			VCenterClient.disconnect();
			return;
		}
		VirtualMachine vm = VMHelper.loadVirtualMachine(vmName);
		if (vm == null) {
			VCenterClient.disconnect();
			return;
		}
		String vmPowerState = VMHelper.getPowerState(vm);

		if (vmPowerState.equals(VMHelper.POWER_OFF)) {
			// Direct starting the vm.
			VMHelper.powerOn(vm);

		} else {
			// in the other case we restart the compute.
			// if (graceful) shutdown guest os and poweron.
			// if cold hard reboot.
			// if warm soft reboot.
			switch (method) {
			case GRACEFUL:
				if (vmPowerState.equals(VMHelper.SUSPENDED)) {
					VMHelper.powerOn(vm);
				}
				VMHelper.graceFulPowerOff(vm);
				VMHelper.powerOn(vm);
				break;
			case COLD:
				if (vmPowerState.equals(VMHelper.SUSPENDED)) {
					VMHelper.powerOn(vm);
				}
				VMHelper.powerOff(vm);
				VMHelper.powerOn(vm);

				break;
			case WARM:
				if (vmPowerState.equals(VMHelper.SUSPENDED)) {
					VMHelper.powerOn(vm);
				}
				VMHelper.rebootGuest(vm);
				break;
			}
		}
		// TODO : Reload vm object with last values.
		this.setState(defineStatus(vmPowerState));

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
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		String vmName = this.getTitle();
		if (vmName == null) {
			LOGGER.error("The title must be set, as it is used as the VM name (unique).");
			VCenterClient.disconnect();
			return;
		}
		VirtualMachine vm = VMHelper.loadVirtualMachine(vmName);
		if (vm == null) {
			VCenterClient.disconnect();
			return;
		}
		String vmPowerState = VMHelper.getPowerState(vm);

		if (vmPowerState.equals(VMHelper.SUSPENDED)) {
			// already suspended.
			LOGGER.info("The virtual machine " + vmName + " is already suspended.");

		} else {
			// in the other case we restart the compute.
			// if hibernate .
			// if acpioff ??
			// if poweroff direct poweroff.
			switch (method) {
			case HIBERNATE:
				VMHelper.hibernateVM(vm);
				break;
			case SUSPEND:
				VMHelper.suspendVM(vm);
				break;
			}
		}
		// TODO : Reload vm object with last values.
		this.setState(defineStatus(vmPowerState));

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
		if (!VCenterClient.checkConnection()) {
			// Must return true if connection is established.
			return;
		}
		String vmName = this.getTitle();
		if (vmName == null) {
			LOGGER.error("The title must be set, as it is used as the VM name (unique).");
			VCenterClient.disconnect();
			return;
		}
		VirtualMachine vm = VMHelper.loadVirtualMachine(vmName);
		if (vm == null) {
			VCenterClient.disconnect();
			return;
		}

		VMHelper.markAsTemplate(vm);

		vm = VMHelper.loadVirtualMachine(vmName);
		String vmPowerState = VMHelper.getPowerState(vm);
		this.setState(defineStatus(vmPowerState));

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

	// /**
	// * if vcenter client connection is not set, this method will connect to
	// * vcenter.
	// */
	// private boolean checkConnection() {
	// if (!VCenterClient.isConnected()) {
	// try {
	// VCenterClient.init();
	// VCenterClient.connect();
	// return true;
	// } catch (IOException ex) {
	// LOGGER.error(ex.getMessage());
	// return false;
	// }
	// } else {
	// return true;
	// }
	//
	// }

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

	/**
	 * Define the corresponding status from VMWare power state.
	 * 
	 * @param vmwarePowerState
	 * @return
	 */
	private ComputeStatus defineStatus(final String vmwarePowerState) {
		ComputeStatus status = this.getState();
		switch (vmwarePowerState) {
		case VMHelper.POWER_ON:
			status = ComputeStatus.ACTIVE;
			break;
		case VMHelper.POWER_OFF:
			status = ComputeStatus.INACTIVE;
			break;
		case VMHelper.SUSPENDED:
			status = ComputeStatus.SUSPENDED;
			break;
		default:
			status = ComputeStatus.ERROR;
		}

		return status;

	}

}
