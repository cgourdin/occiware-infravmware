package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.allocator;

import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.ResourcePool;

/**
 * Define resource allocator interface. (Automatic allocation of resources)
 * @author Christophe Gourdin - Inria
 *
 */
public interface Allocator {
	
	
	public Datacenter allocateDatacenter();
	public Datastore allocateDatastore();
	public ResourcePool allocateResourcePool();
	public ClusterComputeResource allocateCluster();
	public HostSystem allocateHostSystem();
	
	public Network allocateNetwork();
	
	public Datacenter getDc();
	public void setDc(Datacenter dc);

	public Datastore getDatastore();

	public void setDatastore(Datastore datastore);

	public ResourcePool getResourcePool();

	public void setResourcePool(ResourcePool resourcePool);

	public ClusterComputeResource getCluster();

	public void setCluster(ClusterComputeResource cluster);
	
	public void setMemoryHostMini(double memMini);
	public double getMemoryHostMini();
}
