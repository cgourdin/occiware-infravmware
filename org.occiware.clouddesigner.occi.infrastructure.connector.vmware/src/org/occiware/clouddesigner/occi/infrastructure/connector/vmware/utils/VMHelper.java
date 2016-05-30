package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.utils;

import java.rmi.RemoteException;

import org.occiware.clouddesigner.occi.infrastructure.connector.vmware.ComputeConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.Description;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecFileOperation;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualHardware;
import com.vmware.vim25.VirtualLsiLogicController;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineQuickStats;
import com.vmware.vim25.VirtualPCNet32;
import com.vmware.vim25.VirtualSCSISharing;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

/**
 * Helper for Virtual Machine operations.
 * 
 * @author Christophe Gourdin - Inria
 *
 */
public class VMHelper {

	private static Logger LOGGER = LoggerFactory.getLogger(VMHelper.class);
	
	/**
	 * The virtual machine is currently powered off.
	 */
	public static final String POWER_OFF = "poweredOff";
	/**
	 * The virtual machine is currently powered on.
	 */
	public static final String POWER_ON = "poweredOn";
	/**
	 * The virtual machine is currently suspended.
	 */
	public static final String SUSPENDED = "suspended";
	 
	
	/**
	 * Search for a VirtualMachine on tree folder for a name.
	 * 
	 * @param folder
	 * @param name
	 * @return a VirtualMachine object, null if not found.
	 */
	public static VirtualMachine findVMForName(final Folder folder, final String name) {
		VirtualMachine vm = null;

		try {
			vm = (VirtualMachine) new InventoryNavigator(folder).searchManagedEntity(ComputeConnector.VIRTUAL_MACHINE,
					name);

		} catch (RemoteException ex) {
			LOGGER.error("Error while searching a virtual machine : " + name + " --> " + ex.getMessage());
		}

		return vm;
	}

	/**
	 * Search a VM for name and folder.
	 * 
	 * @param folder
	 *            (may be a root folder)
	 * @param name
	 * @return true if the VM exist else false.
	 */
	public static boolean isVMExistForName(final Folder folder, final String name) {

		boolean isVmExist = false;

		VirtualMachine vm = findVMForName(folder, name);
		if (vm != null) {
			isVmExist = true;
		}

		return isVmExist;
	}

	/**
	 * Build a Scsi Device for a new Virtual Machine
	 * 
	 * @param cKey
	 * @return A new Scsi Device
	 */
	public static VirtualDeviceConfigSpec createScsiSpec(int cKey) {
		VirtualDeviceConfigSpec scsiSpec = new VirtualDeviceConfigSpec();
		scsiSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
		VirtualLsiLogicController scsiCtrl = new VirtualLsiLogicController();
		scsiCtrl.setKey(cKey);
		scsiCtrl.setBusNumber(0);
		scsiCtrl.setSharedBus(VirtualSCSISharing.noSharing);
		scsiSpec.setDevice(scsiCtrl);
		return scsiSpec;
	}

	/**
	 * Build a vmDisk Device for a new Virtual Machine
	 * 
	 * @param dsName
	 *            DatastoreName
	 * @param cKey
	 * @param diskSizeKB
	 *            Size of disk in KB
	 * @param diskMode
	 *            Disk Mode: persistent, independent_persistent and
	 *            independent_nonpersistent
	 * @return A new Disk Device
	 */
	public static VirtualDeviceConfigSpec createDiskSpec(String dsName, int cKey, long diskSizeKB, String diskMode) {
		VirtualDeviceConfigSpec diskSpec = new VirtualDeviceConfigSpec();
		diskSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
		diskSpec.setFileOperation(VirtualDeviceConfigSpecFileOperation.create);

		VirtualDisk vd = new VirtualDisk();
		vd.setCapacityInKB(diskSizeKB);
		diskSpec.setDevice(vd);
		vd.setKey(0);
		vd.setUnitNumber(0);
		vd.setControllerKey(cKey);

		VirtualDiskFlatVer2BackingInfo diskfileBacking = new VirtualDiskFlatVer2BackingInfo();
		String fileName = "[" + dsName + "]";
		diskfileBacking.setFileName(fileName);
		diskfileBacking.setDiskMode(diskMode);
		diskfileBacking.setThinProvisioned(true);

		vd.setBacking(diskfileBacking);

		return diskSpec;
	}

	/**
	 * 
	 * @param netName
	 * @param nicName
	 * @return
	 * @throws Exception
	 */
	public static VirtualDeviceConfigSpec createNicSpec(String netName, String nicName) {

		VirtualDeviceConfigSpec nicSpec = new VirtualDeviceConfigSpec();
		nicSpec.setOperation(VirtualDeviceConfigSpecOperation.add);

		VirtualEthernetCard nic = new VirtualPCNet32();
		VirtualEthernetCardNetworkBackingInfo nicBacking = new VirtualEthernetCardNetworkBackingInfo();
		nicBacking.setDeviceName(netName);

		Description info = new Description();
		info.setLabel(nicName);
		info.setSummary(netName);
		nic.setDeviceInfo(info);

		// type: "generated", "manual", "assigned" by VC
		nic.setAddressType("generated");
		nic.setBacking(nicBacking);
		nic.setKey(0);

		nicSpec.setDevice(nic);
		return nicSpec;
	}

	/**
	 * Set or unset the hotReconfig option. (to reconfig a vm cpu and memory,
	 * hot mode)
	 * 
	 * @param folder
	 *            (folder to search vm)
	 * @param name
	 *            (name of the vm)
	 * @param hotReconfig,
	 *            if true, hotReconfig is enabled, else disabled.
	 */
	public static void hotReconfigEnable(final Folder folder, String name, boolean hotReconfig) {
		// Search the vm.
		VirtualMachine vm = VMHelper.findVMForName(folder, name);
		Task task = null;
		VirtualMachineConfigSpec changeSpec = new VirtualMachineConfigSpec();
		changeSpec.setCpuHotAddEnabled(true);
		changeSpec.setMemoryHotAddEnabled(true);
		boolean retVal = false;
		if (vm != null && (!vm.getConfig().getCpuHotAddEnabled() || !vm.getConfig().getMemoryHotAddEnabled())) {
			try {
				task = vm.powerOffVM_Task();
			} catch (RemoteException ex) {
				LOGGER.error("Unable to enable/disable hot reconfiguration of this virtual machine : " + vm.getName());
				LOGGER.error("Cause : " + ex.getMessage());
				return;
			}
			if (task != null) {
				try {
					retVal = task.waitForTask().equals(Task.SUCCESS);
					if (retVal) {
						LOGGER.info("VM " + vm.getName() + " powered off");
					} else {
						LOGGER.info("VM cant poweroff");
						LOGGER.error("Unable to enable/disable hot reconfiguration of this virtual machine : "
								+ vm.getName());
						return;
					}

				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (RuntimeFault e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (retVal && !vm.getRuntime().getPowerState().equals(VirtualMachinePowerState.poweredOff)) {
				// this vm is poweredoff.
				if (task != null) {
					try {
						retVal = task.waitForTask().equals(Task.SUCCESS);
						if (retVal) {
							LOGGER.info(
									"VM " + name + " configuration updated - HotAdd plugin enabled for CPU and Memory");
						} else {
							LOGGER.info("VM " + name + " cannot be reconfigured");
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (RuntimeFault e) {
						e.printStackTrace();
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
			}

		}
	}

	/**
	 * Mount guest vmware tools, it's important to make it accessible directly
	 * to client.
	 * 
	 * @param folder
	 * @param name
	 */
	public static void mountGuestVmTools(final Folder folder, final String name) {
		VirtualMachine vm = VMHelper.findVMForName(folder, name);
		try {
			vm.mountToolsInstaller();
		} catch (RemoteException ex) {
			LOGGER.info("Unable to mount VMWare tools installer !");
		}

	}

	/**
	 * Find an hostSystem for a vm, if there is hostSystemName information, and
	 * compare with the hosted vmName for each vms of each host.
	 * 
	 * @param vmName
	 * @return a hostSystem name, may return null if not found.
	 */
	public static String findHostSystemNameForVM(final Folder rootFolder, final String vmName) {
		String hostSystemName = null;
		try {

			boolean found = false;
			HostSystem hostSystem = null;
			ManagedEntity[] hostsystems = new InventoryNavigator(rootFolder).searchManagedEntities("HostSystem");
			for (ManagedEntity hostsystem : hostsystems) {
				HostSystem host = (HostSystem) hostsystem;

				VirtualMachine[] vms = host.getVms();

				for (VirtualMachine vm : vms) {
					if (vm.getName().equals(vmName)) {
						hostSystem = host;
						found = true;
						break;
					}
				}

				if (found)
					break;
			}
			hostSystemName = hostSystem.getName();

		} catch (RemoteException ex) {
			LOGGER.error("cant find the hostname of this virtual machine : " + vmName);
		}

		return hostSystemName;
	}

	/**
	 * Find an hostSystem Object for a vm, if there is hostSystem information,
	 * and compare with the hosted vmName for each vms of each host.
	 * 
	 * @param vmName
	 * @return a hostSystem, may return null if not found.
	 */
	public static HostSystem findHostSystemForVM(final Folder rootFolder, final String vmName) {
		HostSystem hostSystem = null;
		try {

			boolean found = false;

			ManagedEntity[] hostsystems = new InventoryNavigator(rootFolder).searchManagedEntities("HostSystem");
			for (ManagedEntity hostsystem : hostsystems) {
				HostSystem host = (HostSystem) hostsystem;

				VirtualMachine[] vms = host.getVms();

				for (VirtualMachine vm : vms) {
					if (vm.getName().equals(vmName)) {
						hostSystem = host;
						found = true;
						break;
					}
				}

				if (found)
					break;
			}

		} catch (RemoteException ex) {
			LOGGER.error("cant find the hostname of this virtual machine : " + vmName);
		}

		return hostSystem;
	}

	/**
	 * Get the number of cpu of this virtual machine.
	 * 
	 * @param vm
	 * @return an Integer that represents the number of cpu.
	 */
	public static Integer getNumCPU(final VirtualMachine vm) {
		Integer cpus = 0;
		if (vm != null) {
			VirtualHardware hw = vm.getConfig().getHardware();
			cpus = hw.getNumCPU();
		}
		return cpus;
	}

	/**
	 * Get the number of core per socket.
	 * 
	 * @param vm
	 * @return an Integer.
	 */
	public static Integer getNumCorePerSocket(final VirtualMachine vm) {
		Integer coresPerSocket = 0;
		if (vm != null) {
			VirtualHardware hw = vm.getConfig().getHardware();
			coresPerSocket = hw.getNumCoresPerSocket();
		}
		return coresPerSocket;

	}

	/**
	 * Get the memory size in GigaBytes.
	 * 
	 * @param vm
	 * @return a Float
	 */
	public static Float getMemoryGB(final VirtualMachine vm) {
		Float memoryGB = 0.0f;
		if (vm != null) {
			VirtualHardware hw = vm.getConfig().getHardware();
			Integer memMB = hw.getMemoryMB();
			Float memMBFT = memMB.floatValue();
			memoryGB = memMBFT / 1024;
		}

		return memoryGB;
	}

	/**
	 * Get the architecture (occi format) x86 (32 bits) or x64 (64 bits).
	 * 
	 * @param vm
	 * @return a String
	 */
	public static String getArchitecture(final VirtualMachine vm) {
		String architecture = "x86";
		if (vm != null) {
			String guestFullName = vm.getConfig().getGuestFullName();
			if (guestFullName != null && guestFullName.contains("64")) {
				architecture = "x64";
			} // elsewere this is not defined by vmware.

		}

		return architecture;
	}

	/**
	 * Get the cpu speed in Ghz.
	 * 
	 * @param vm
	 * @return a Float, the cpu speed current demand by the vm, there is no
	 *         value on exact cpu speed at this time through the VMWare api.
	 */
	public static Float getCPUSpeed(final VirtualMachine vm) {
		Float cpuSpeed = 0.0f;
		if (vm != null) {
			VirtualMachineQuickStats qstats = vm.getSummary().getQuickStats();
			// In MHz.
			Integer cpuSpeedDemand = vm.getSummary().getQuickStats().getOverallCpuDemand(); 
			// In Ghz.
			cpuSpeed = cpuSpeedDemand.floatValue() / 1000;
		}

		return cpuSpeed;
	}

	/**
	 * Get the state of this compute (VMWare format).
	 * poweredOff The virtual machine is currently powered off.
	 * poweredOn The virtual machine is currently powered on.
	 * suspended The virtual machine is currently suspended.
	 * @param vm
	 * @return a String
	 */
	public static String getPowerState(final VirtualMachine vm) {
		// Get the current state of this vm.
		String state = null;
		if (vm != null) {
			state = vm.getSummary().getRuntime().getPowerState().name();
		}
		return state;
	}
	
	/**
	 * Operation mode of guest operating system, via guestInfo.
	 * "running" - Guest is running normally.
	 * "shuttingdown" - Guest has a pending shutdown command.
	 * "resetting" - Guest has a pending reset command.
	 * "standby" - Guest has a pending standby command.
	 * "notrunning" - Guest is not running.
	 * "unknown" - Guest information is not available.
	 * @param vm
	 * @return
	 */
	public static String getGuestState(final VirtualMachine vm) {
		String state = null;
		if (vm != null) {
			state = vm.getGuest().getGuestState();
		}
		return state;
	}
	
	/**
	 * Get the guest hostname if known.
	 * @param vm
	 * @return if the guest hostname is not known, this may return null.
	 */
	public static String getGuestHostname(final VirtualMachine vm) {
		String hostname = null;
		if (vm != null) {
			hostname = vm.getGuest().getHostName();
		}
		return hostname;
	}
	
}
