/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 * 
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package cloudsim;

import gridsim.GridSim;
import gridsim.Gridlet;
import gridsim.ResGridlet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cloudsim.CloudSim;
import cloudsim.ext.Constants;

/**
 * CloudletSchedulerTimeShared implements a policy of scheduling performed by a virtual machine.
 * Cloudlets execute time-shared in VM.
 * 
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public class CloudletSchedulerTimeShared extends VMScheduler {

	/** The cloudlet exec list. */
	private List<? extends ResGridlet> cloudletExecList;

	/** The cloudlet paused list. */
	private List<? extends ResGridlet> cloudletPausedList;

	/** The cloudlet finished list. */
	private List<? extends ResGridlet> cloudletFinishedList;

	/** The current cp us. */
	protected int currentCPUs;

	private double[] currentMipsShare;

	/**
	 * Creates a new CloudletSchedulerTimeShared object. This method must be invoked before starting
	 * the actual simulation.
	 * 
	 * @pre $none
	 * @post $none
	 */
	public CloudletSchedulerTimeShared() {
		super();
		cloudletExecList = new ArrayList<ResGridlet>();
		cloudletPausedList = new ArrayList<ResGridlet>();
		cloudletFinishedList = new ArrayList<ResGridlet>();
		currentCPUs = 0;
	}

	/**
	 * Sets the current mips share.
	 * 
	 * @param mipsShare the new current mips share
	 */
	protected void setCurrentMipsShare(double[] mipsShare) {
		this.currentMipsShare = mipsShare;
	}

	/**
	 * Gets the status of a gridlet
	 * @param glId ID of the gridlet
	 * @return status of the gridlet, -1 if gridlet not found
	 * @pre $none
	 * @post $none
	 */
	@Override
	public int cloudletstatus(int glId) {
		
		Iterator iter = cloudletExecList.iterator();
		while(iter.hasNext()){
			ResGridlet rgl = (ResGridlet) iter.next();
			if (rgl.getGridletID()==glId) return rgl.getGridletStatus();
		}
		iter = cloudletPausedList.iterator();
		while(iter.hasNext()){
			ResGridlet rgl = (ResGridlet) iter.next();
			if (rgl.getGridletID()==glId) return rgl.getGridletStatus();
		}
		return -1;
	}	
	
	/**
	 * Updates the processing of cloudlets running under management of this scheduler.
	 * 
	 * @param currentTime current simulation time
	 * @param mipsShare array with MIPS share of each processor available to the scheduler
	 * @return time predicted completion time of the earliest finishing cloudlet, or 0 if there is
	 *         no next events
	 * @pre currentTime >= 0
	 * @post $none
	 */
	@Override
	public double updateVMProcessing(double currentTime, double[]mipsShare) {
		setCurrentMipsShare(mipsShare);
		double timeSpam = currentTime - previousTime;
		
		//Wondra to be able to remove from the list while iterating over it
		List<ResGridlet> oldlist = new ArrayList<ResGridlet>();
		oldlist.addAll(getCloudletExecList());
		
		for (ResGridlet rcl : oldlist) {
			//System.out.print("Cloudlet remaining before:"+rcl.getRemainingGridletLength());
			rcl.updateGridletFinishedSoFar(getCapacity(mipsShare) * timeSpam * rcl.getNumPE());
			if (rcl.getRemainingGridletLength()<0.01) rcl.updateGridletFinishedSoFar(rcl.getRemainingGridletLength());
			//System.out.println(" per step: "+getCapacity(mipsShare) * timeSpam * rcl.getNumPE()+" after: "+rcl.getRemainingGridletLength());
		    /*if (rcl.getRemainingGridletLength()==0) {
		    	cloudletFinish(rcl);
		    	getCloudletExecList().remove(rcl);
		    }*/
		}

		if (getCloudletExecList().size() == 0) {
			previousTime=currentTime;
			return 0.0;
		} else {
			//System.out.println("Active step size: "+timeSpam);
		}

		double nextEvent = Double.MAX_VALUE;
		
		// check finished cloudlets
		List<ResGridlet> toRemove = new ArrayList<ResGridlet>();
		for (ResGridlet rcl : getCloudletExecList()) {
			double remainingLength = rcl.getRemainingGridletLength();
			if (remainingLength == 0) {// finished: remove from the list
				toRemove.add(rcl);
				cloudletFinish(rcl);
				continue;
			}
		}
		getCloudletExecList().removeAll(toRemove);

		// estimate finish time of cloudlets
		for (ResGridlet rcl : getCloudletExecList()) {
			double estimatedFinishTime = currentTime
					+ (rcl.getRemainingGridletLength() / (getCapacity(mipsShare) * rcl.getNumPE()));
			if (estimatedFinishTime - currentTime < 0.01 /*CloudSim.getMinTimeBetweenEvents()*/) {
				estimatedFinishTime = currentTime + 0.01 /*CloudSim.getMinTimeBetweenEvents()*/;
			}

			if (estimatedFinishTime < nextEvent) {
				nextEvent = estimatedFinishTime;
			}
		}

		previousTime=currentTime;
		return nextEvent;
	}

	/**
	 * Gets the capacity.
	 * 
	 * @param mipsShare the mips share
	 * @return the capacity
	 */
	protected double getCapacity(double[] mipsShare) {
		double capacity = 0.0;
		int cpus = 0;
		for(int i=0;i<mipsShare.length;i++){
			capacity+=mipsShare[i];
			if(mipsShare[i]>0)cpus++;
			//System.out.println("- MIPSShare[i]:"+i+" "+mipsShare[i]);
		}
		currentCPUs = cpus;

		int pesInUse = 0;
		Iterator iter = cloudletExecList.iterator();
		while(iter.hasNext()){
			ResGridlet rcl = (ResGridlet) iter.next();		
			pesInUse += rcl.getNumPE();
		}
		//System.out.println("- pesInUse:"+pesInUse);
		//System.out.println("- Capacity total:"+capacity);
		if (pesInUse > currentCPUs) {
			capacity /= pesInUse;
		} else {
			capacity /= currentCPUs;
		}
		//System.out.println("- Capacity per gridlet:"+capacity);
		
		return capacity;
	}

	/**
	 * Cancels execution of a cloudlet.
	 * 
	 * @param cloudletId ID of the cloudlet being cancealed
	 * @return the canceled cloudlet, $null if not found
	 * @pre $none
	 * @post $none
	 */
	@Override
	public Cloudlet cloudletCancel(int cloudletId) {
		System.out.println("cancel");
		boolean found = false;
		int position = 0;

		// First, looks in the finished queue
		found = false;
		for (ResGridlet rcl : getCloudletFinishedList()) {
			if (rcl.getGridletID() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}

		if (found) {
			return (Cloudlet) getCloudletFinishedList().remove(position).getGridlet();
		}

		// Then searches in the exec list
		position=0;
		for (ResGridlet rcl : getCloudletExecList()) {
			if (rcl.getGridletID() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}

		if (found) {
			ResGridlet rcl = getCloudletExecList().remove(position);
			if (rcl.getRemainingGridletLength() == 0) {
				cloudletFinish(rcl);
			} else {
				rcl.setGridletStatus(Cloudlet.CANCELED);
			}
			return (Cloudlet) rcl.getGridlet();
		}

		// Now, looks in the paused queue
		found = false;
		position=0;
		for (ResGridlet rcl : getCloudletPausedList()) {
			if (rcl.getGridletID() == cloudletId) {
				found = true;
				rcl.setGridletStatus(Cloudlet.CANCELED);
				break;
			}
			position++;
		}

		if (found) {
			return (Cloudlet) getCloudletPausedList().remove(position).getGridlet();
		}
		
		return null;
	}

	/**
	 * Pauses execution of a cloudlet.
	 * 
	 * @param cloudletId ID of the cloudlet being paused
	 * @return $true if cloudlet paused, $false otherwise
	 * @pre $none
	 * @post $none
	 */
	@Override
	public boolean cloudletPause(int cloudletId) {
		System.out.println("pause");
		boolean found = false;
		int position = 0;

		for (ResGridlet rcl : getCloudletExecList()) {
			if (rcl.getGridletID() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}
		
		if (found) {
			// remove cloudlet from the exec list and put it in the paused list
			ResGridlet rcl = getCloudletExecList().remove(position);
			if (rcl.getRemainingGridletLength() == 0) {
				cloudletFinish(rcl);
			} else {
				rcl.setGridletStatus(Cloudlet.PAUSED);
				getCloudletPausedList().add(rcl);
			}
			return true;
		}
		return false;
	}

	/**
	 * Processes a finished cloudlet.
	 * 
	 * @param rcl finished cloudlet
	 * @pre rgl != $null
	 * @post $none
	 */
	private void cloudletFinish(ResGridlet rcl) {
		rcl.setGridletStatus(Cloudlet.SUCCESS);
		rcl.finalizeGridlet();
		getCloudletFinishedList().add(rcl);
	}

	/**
	 * Resumes execution of a paused cloudlet.
	 * 
	 * @param cloudletId ID of the cloudlet being resumed
	 * @return expected finish time of the cloudlet, 0.0 if queued
	 * @pre $none
	 * @post $none
	 */
	@Override
	public double cloudletResume(int cloudletId) {
		System.out.println("resume");
		boolean found = false;
		int position = 0;

		// look for the cloudlet in the paused list
		for (ResGridlet rcl : getCloudletPausedList()) {
			if (rcl.getGridletID() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}

		if (found) {
			ResGridlet rgl = getCloudletPausedList().remove(position);
			rgl.setGridletStatus(Cloudlet.INEXEC);
			getCloudletExecList().add(rgl);

			// calculate the expected time for cloudlet completion
			// first: how many PEs do we have?

			double remainingLength = rgl.getRemainingGridletLength();
			double estimatedFinishTime = GridSim.clock()
					+ (remainingLength / (getCapacity(currentMipsShare) * rgl.getNumPE()));

			return estimatedFinishTime;
		}

		return 0.0;
	}

	/**
	 * Receives an cloudlet to be executed in the VM managed by this scheduler.
	 * 
	 * @param cloudlet the submited cloudlet
	 * @param fileTransferTime time required to move the required files from the SAN to the VM
	 * @return expected finish time of this cloudlet
	 * @pre gl != null
	 * @post $none
	 */
	@Override
	public double cloudletSubmit(Cloudlet cloudlet, double fileTransferTime) {
		ResGridlet rcl = new ResGridlet(cloudlet);
		rcl.setGridletStatus(Cloudlet.INEXEC);
		for (int i = 0; i < cloudlet.getNumPE(); i++) {
			rcl.setMachineAndPEID(0, i);
		}

		getCloudletExecList().add(rcl);

		// use the current capacity to estimate the extra amount of
		// time to file transferring. It must be added to the cloudlet length
		double extraSize = getCapacity(currentMipsShare) * fileTransferTime;
		long length = (long) (cloudlet.getGridletLength() + extraSize);
		cloudlet.setGridletLength(length);
		//System.out.println("On submit-GridletLength,capacity:"+cloudlet.getGridletLength()+" "+getCapacity(currentMipsShare));
		
		return cloudlet.getGridletLength() / getCapacity(currentMipsShare);
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.CloudletScheduler#cloudletSubmit(cloudsim.Cloudlet)
	 */
	public double cloudletSubmit(Cloudlet cloudlet) {
		return cloudletSubmit(cloudlet, 0.0);
	}

	/**
	 * Gets the status of a cloudlet.
	 * 
	 * @param cloudletId ID of the cloudlet
	 * @return status of the cloudlet, -1 if cloudlet not found
	 * @pre $none
	 * @post $none
	 */
	public int getCloudletStatus(int cloudletId) {
		for (ResGridlet rcl : getCloudletExecList()) {
			if (rcl.getGridletID() == cloudletId) {
				return rcl.getGridletStatus();
			}
		}
		for (ResGridlet rcl : getCloudletPausedList()) {
			if (rcl.getGridletID() == cloudletId) {
				return rcl.getGridletStatus();
			}
		}
		return -1;
	}

	/**
	 * Get utilization created by all cloudlets.
	 * 
	 * @param time the time
	 * @return total utilization
	 */
/*	public double getTotalUtilizationOfCpu(double time) {
		double totalUtilization = 0;
		for (ResGridlet gl : getCloudletExecList()) {
			totalUtilization += gl.getGridlet(). getUtilizationOfCpu(time);
		}
		return totalUtilization;
	}
*/
	/**
	 * Informs about completion of some cloudlet in the VM managed by this scheduler.
	 * 
	 * @return $true if there is at least one finished cloudlet; $false otherwise
	 * @pre $none
	 * @post $none
	 */
	@Override
	public boolean isFinishedCloudlets() {
		return getCloudletFinishedList().size() > 0;
	}

	/**
	 * Returns the next cloudlet in the finished list, $null if this list is empty.
	 * 
	 * @return a finished cloudlet
	 * @pre $none
	 * @post $none
	 */
	@Override
	public Cloudlet getNextFinishedCloudlet() {
		if (getCloudletFinishedList().size() > 0) {
			return (Cloudlet) getCloudletFinishedList().remove(0).getGridlet();
		}
		return null;
	}

	/**
	 * Returns the number of cloudlets runnning in the virtual machine.
	 * 
	 * @return number of cloudlets runnning
	 * @pre $none
	 * @post $none
	 */
	@Override
	public int runningCloudlets() {
		return getCloudletExecList().size();
	}

	/**
	 * Returns one cloudlet to migrate to another vm.
	 * 
	 * @return one running cloudlet
	 * @pre $none
	 * @post $none
	 */
	@Override
	public Cloudlet migrateCloudlet() {
		ResGridlet rgl = getCloudletExecList().remove(0);
		rgl.finalizeGridlet();
		return (Cloudlet) rgl.getGridlet();
	}

	/**
	 * Gets the cloudlet exec list.
	 * 
	 * @param <T> the generic type
	 * @return the cloudlet exec list
	 */
	@SuppressWarnings("unchecked")
	protected <T extends ResGridlet> List<T> getCloudletExecList() {
		return (List<T>) cloudletExecList;
	}

	/**
	 * Sets the cloudlet exec list.
	 * 
	 * @param <T> the generic type
	 * @param cloudletExecList the new cloudlet exec list
	 */
	protected <T extends ResGridlet> void setCloudletExecList(List<T> cloudletExecList) {
		this.cloudletExecList = cloudletExecList;
	}

	/**
	 * Gets the cloudlet paused list.
	 * 
	 * @param <T> the generic type
	 * @return the cloudlet paused list
	 */
	@SuppressWarnings("unchecked")
	protected <T extends ResGridlet> List<T> getCloudletPausedList() {
		return (List<T>) cloudletPausedList;
	}

	/**
	 * Sets the cloudlet paused list.
	 * 
	 * @param <T> the generic type
	 * @param cloudletPausedList the new cloudlet paused list
	 */
	protected <T extends ResGridlet> void setCloudletPausedList(List<T> cloudletPausedList) {
		this.cloudletPausedList = cloudletPausedList;
	}

	/**
	 * Gets the cloudlet finished list.
	 * 
	 * @param <T> the generic type
	 * @return the cloudlet finished list
	 */
	@SuppressWarnings("unchecked")
	protected <T extends ResGridlet> List<T> getCloudletFinishedList() {
		return (List<T>) cloudletFinishedList;
	}

	/**
	 * Sets the cloudlet finished list.
	 * 
	 * @param <T> the generic type
	 * @param cloudletFinishedList the new cloudlet finished list
	 */
	protected <T extends ResGridlet> void setCloudletFinishedList(List<T> cloudletFinishedList) {
		this.cloudletFinishedList = cloudletFinishedList;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.CloudletScheduler#getCurrentRequestedMips()
	 */
	public List<Double> getCurrentRequestedMips() {
		List<Double> mipsShare = new ArrayList<Double>();
		return mipsShare;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.CloudletScheduler#getTotalCurrentAvailableMipsForCloudlet(cloudsim.ResGridlet,
	 * java.util.List)
	 */
	public double getTotalCurrentAvailableMipsForCloudlet(ResGridlet rcl, List<Double> mipsShare) {
		return getCapacity(currentMipsShare);
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.CloudletScheduler#getTotalCurrentAllocatedMipsForCloudlet(cloudsim.ResGridlet,
	 * double)
	 */
	public double getTotalCurrentAllocatedMipsForCloudlet(ResGridlet rcl, double time) {
		return 0.0;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.CloudletScheduler#getTotalCurrentRequestedMipsForCloudlet(cloudsim.ResGridlet,
	 * double)
	 */
	public double getTotalCurrentRequestedMipsForCloudlet(ResGridlet rcl, double time) {
		// TODO Auto-generated method stub
		return 0.0;
	}

/*	public double getCurrentRequestedUtilizationOfRam() {
		double ram = 0;
		for (ResGridlet cloudlet : cloudletExecList) {
			ram += cloudlet.getGridlet().getUtilizationOfRam(GridSim.clock());
		}
		return ram;
	}

	public double getCurrentRequestedUtilizationOfBw() {
		double bw = 0;
		for (ResGridlet cloudlet : cloudletExecList) {
			bw += cloudlet.getGridlet().getUtilizationOfBw(GridSim.clock());
		}
		return bw;
	}
*/
}
