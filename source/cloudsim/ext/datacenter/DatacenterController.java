package cloudsim.ext.datacenter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/*
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Vm;*/

import cloudsim.DataCenter;
import cloudsim.DatacenterBroker;
import cloudsim.DatacenterCharacteristics;
import cloudsim.DatacenterTags;
import cloudsim.VMCharacteristics;
import cloudsim.VMScheduler;
import cloudsim.VirtualMachine;
import cloudsim.VirtualMachineList;
import cloudsim.ext.Constants;
import cloudsim.ext.Extra;
import cloudsim.ext.GeoLocatable;
import cloudsim.ext.InternetCharacteristics;
import cloudsim.ext.InternetCloudlet;
import cloudsim.ext.event.CloudSimEvent;
import cloudsim.ext.event.CloudSimEventListener;
import cloudsim.ext.event.CloudSimEvents;
import cloudsim.ext.event.CloudsimObservable;
import cloudsim.ext.gui.utils.SimMeasure;
import cloudsim.ext.stat.HourlyEventCounter;
import cloudsim.ext.stat.HourlyStat;
import cloudsim.ext.util.CommPath;
import cloudsim.ext.util.InternetEntitityRegistry;
import eduni.simjava.Sim_event;
import eduni.simjava.Sim_stat;
import eduni.simjava.Sim_system;
import gridsim.GridSim;
import gridsim.GridSimTags;

/**
 * DataCenterController is responsible for managing a single Data Center ({@link DataCenter}).
 * It extends {@link DatacenterBroker} and overrides some of the default behaviour to be 
 * responsible only for managing a single data center.
 * 
 * @author Bhathiya Wickremasinghe
 *
 */
public class DatacenterController extends DatacenterBroker implements GeoLocatable,
CloudsimObservable,
Constants {


	private List<CloudSimEventListener> listeners;
	private VmLoadBalancer loadBalancer;
	private int region;
	private Sim_stat stat;
	private int queuedCount = 0;
	private double costPerVmHour;
	private double costPerDataGB;
	private double totalData;
	private HourlyEventCounter hourlyArrival;
	private HourlyStat hourlyProcessingTimes;
	private Map<Integer, Double[]> vmUsage;
	private Map<Integer, VirtualMachineState> vmStatesList;
	private Map<Integer, Long[]> processingCloudletStatuses;
	private int requestsPerCloudlet;
	private List<InternetCloudlet> waitingQueue;
	private String dcName;
	private boolean lastVmCreateFailed = false;
	private int allRequestsProcessed = 0;

	/** Constructor. */
	public DatacenterController(String name, 
			int region, 
			double costPerVmHour, 
			double costPerDataGB,
			int requestsPerCloudlet,
			String loadBalancePolicy) throws Exception {
		super(name + "-Broker");

		this.dcName = name;

		System.out.println("Creating new broker " + get_name());

		listeners =new ArrayList<CloudSimEventListener>();

		this.region = region;
		this.costPerVmHour = costPerVmHour;
		this.costPerDataGB = costPerDataGB;
		this.requestsPerCloudlet = requestsPerCloudlet;

		InternetCharacteristics.getInstance().addEntity(this);

		stat = new Sim_stat();
		stat.add_measure(DC_SERVICE_TIME, Sim_stat.INTERVAL_BASED);

		hourlyProcessingTimes = new HourlyStat(stat, "Overloading status : " + get_name(), Sim_stat.INTERVAL_BASED);
		set_stat(stat);	

		hourlyArrival = new HourlyEventCounter("Hourly Arrival Rate : " + get_name());

		vmUsage = new HashMap<Integer, Double[]>();
		vmStatesList = Collections.synchronizedMap(new HashMap<Integer, VirtualMachineState>());
		waitingQueue = Collections.synchronizedList(new LinkedList<InternetCloudlet>());
		processingCloudletStatuses = new HashMap<Integer, Long[]>();

		if (loadBalancePolicy.equals(Constants.LOAD_BALANCE_ACTIVE)){
			this.loadBalancer = new ActiveVmLoadBalancer(this);
		} else if (loadBalancePolicy.equals(Constants.LOAD_BALANCE_POLICY_RR)){
			this.loadBalancer = new RoundRobinVmLoadBalancer(vmStatesList);
		} else { //i.e. if (loadBalancePolicy.equals(Constants.LOAD_BALANCE_THROTTLED))
			this.loadBalancer = new ThrottledVmLoadBalancer(this);
		}

	}


	/**
	 * Handles external events that are coming to this DatacenterBroker entity.
	 * This method also queries <tt>GridInformationService</tt> about available
	 * datacenters.
	 * @pre $none
	 * @post $none
	 */
	@Override
	public void body(){

		System.out.println("Starting broker " + get_id() + " name=" + get_name());
		//queries GIS about available datacenters
		sim_process(5.0);
		LinkedList datacenterList = GridSim.getGridResourceList();
		System.out.println(GridSim.clock()+": "+this.get_name()+ ": Cloud Resource List received with "+datacenterList.size()+" resource(s)");

		//initilize fields
		this.datacenters= 1;
		this.datacenterID=new int[1];
		this.datacenterChar=new DatacenterCharacteristics[1];
		datacenterChar[0] = null;

		//queries datacenters about their characteristics
		this.datacenterID[0] = GridSim.getEntityId(dcName);
		send(dcName, GridSimTags.SCHEDULE_NOW, GridSimTags.RESOURCE_CHARACTERISTICS, this.get_id());

		//receives events and process them
		Sim_event ev = new Sim_event();



		int totalMeasures=25*20;//each 5 mins, using 25 hours
		double granularity=0.05; //also 5 minutes
		int cont =0;
		double[] measures=new double[totalMeasures];
		int[] checked= new int[totalMeasures];
		for (int i=0;i<totalMeasures;i++){
			checked[i]=0;
		}
		while(Sim_system.running()){
			super.sim_get_next(ev);
			cont++;
			double currTime = GridSim.clock();

			if (cont%200==0){
				System.out.print((int)(currTime/60000)+" m\n");

			}

			if(checked[(int)(currTime/(granularity*Constants.MILLI_SECONDS_TO_HOURS))]==0){

				@SuppressWarnings("rawtypes")
				List res = stat.get_measures();
				//int hora=0;
				for (Object o : res) {
					Object[] oArray = (Object[]) o;
					String measure = (String) oArray[0];
					//DecimalFormat decimales = new DecimalFormat("0.0");
					if(stat.average(measure)!=0){
						//System.out.print(hora+".00 - "+(hora+1)+".00 -> Average: "+decimales.format(stat.average(measure))+"\tMinimum: "+decimales.format(stat.minimum(measure))+"\tMaximum: "+decimales.format(stat.maximum(measure))+"\n");
						measures[(int)(currTime/(granularity*Constants.MILLI_SECONDS_TO_HOURS))]=stat.average(measure);
					}
					//hora++;
				}
				/*if (checked[50]==1&&checked[51]==0)
					addVMs(10);
				if (checked[54]==1&&checked[55]==0)
					removeVMs(10);*/
				tresholdFunction(measures, (int)(currTime/(granularity*Constants.MILLI_SECONDS_TO_HOURS)));


				checked[(int)(currTime/(granularity*Constants.MILLI_SECONDS_TO_HOURS))]=1;
			}

			// if the simulation finishes then exit the loop
			if (ev.get_tag() == GridSimTags.END_OF_SIMULATION){
				System.out.print("+++Ending message has arrived to DCcontroller+++");
				break;
			}

			// process the received event
			processEvent(ev);
		}

		for(int i=0;i<totalMeasures;i++){
			System.out.print(i+" "+measures[i]+"\n");
		}

		System.out.println(get_name() + " finalizing, submitted cloudlets=" + cloudletsSubmitted 
				+ " processing cloudlets=" + processingCloudletStatuses.size() + " ,allRequestsProcessed=" + allRequestsProcessed);
	
		double totalTime=Extra.getDuration();//24*60*60*1000.0;
		totalTime*=myDestroyedVMs;
		double totalSavedTime= totalTime-mySavedTime;
		System.out.print("\n"+"Saved money turning off machines: "+((totalSavedTime*Extra.getpriceVMHour())/(60*60*1000))+"\n");
		
	}//body

	private void tresholdFunction(double[] measures, int currentPos){
		boolean analise=true;
		double count=0;
		int number =4;
		for(int i=currentPos;i>Math.max(currentPos-number,1);i--){
			if(measures[i]==0){
				analise=false;
				break;
			}
			else
				count+=measures[i];
		}
		if(analise){
			count/=number;
			if(count<190)
				removeVMs(1);
			else 
				if(count>330)
					addVMs(1);
		}

	}

	@Override
	protected void processEvent(Sim_event ev) {
		Object payload = ev.get_data();

		//Filter out in bound InternetCloudlet events and returning cloudlet events.
		// Let the super handle the rest.
		if (ev.get_tag()== Constants.REQUEST_INTERNET_CLOUDLET_TAG){
			if ((payload != null) && (payload instanceof InternetCloudlet)){	
				handleRequestCloudlet((InternetCloudlet) payload);
			} else {
				System.out.println("invalid payload");
			}
		} else if ((ev.get_tag() == GridSimTags.GRIDLET_RETURN) && (payload instanceof InternetCloudlet)){
			if ((payload != null) && (payload instanceof InternetCloudlet)){
				InternetCloudlet cl = (InternetCloudlet) payload;

				handleResponseCloudlet(cl);
			}
		} else {
			super.processEvent(ev);
		}		
	}


	/**
	 * Handle responses returning for request previously handled.
	 * @param cl
	 */
	private void handleResponseCloudlet(InternetCloudlet cl) {

		cloudletCompletedProcessing(cl);

		int parentRequest = cl.getParentId();
		Long[] parentReqStatus = processingCloudletStatuses.remove(parentRequest);

		if (parentReqStatus == null){
			System.out.println("Got response for " + parentRequest + " but it seems to be completed.");
			return;
		}

		long totalRequests = parentReqStatus[0];
		long completedRequests = parentReqStatus[1] + cl.getRequestCount();
		final long startTime = parentReqStatus[2];

		//If this is the first response received for the orginal request, send response back to UserBase
		// Simulation should capture the time for single request, not all
		if (parentReqStatus[1] == 0){
			double endTime = GridSim.clock();
			double thisProcessingTime = (endTime - startTime);

			InternetCloudlet responseCloudlet = new InternetCloudlet(parentRequest, 
					0, 
					(long) (cl.getDataSize() / cl.getRequestCount()), 
					0, 
					cl.getOriginator(), 
					cl.getAppId(), 
					(int) totalRequests);
			responseCloudlet.addData("procTime", thisProcessingTime);
			send("Internet", 0.0, Constants.RESPONSE_INTERNET_CLOUDLET_TAG, responseCloudlet);

			stat.update(DC_SERVICE_TIME, startTime, endTime);
			hourlyProcessingTimes.update(startTime, endTime);

			//System.out.println(endTime + ": DC processing time for " + parentRequest + "=" + thisProcessingTime + " in vm " + cl.getVmId() + " and current processingqueue=" + processingCloudletStatuses.size());

			InternetCharacteristics.getInstance().updateSerivceLatency(get_name(), thisProcessingTime);
		}

		//But keep on executing rest of the cloudlets running to keep the Data Center loaded
		if (completedRequests >= totalRequests){
			allRequestsProcessed += totalRequests;

		} else {
			//System.out.println(GridSim.clock() + ":" + get_name() + " processing " + parentRequest + " " + completedRequests + "/" + totalRequests + " complete");

			processingCloudletStatuses.put(parentRequest, new Long[]{totalRequests, completedRequests, startTime});
		}

		totalData += cl.getDataSize();

	}


	/** 
	 * Handle an incoming request. Requests are bundled to UserBaseGroupingFactor. 
	 * This method repackages them to DcRequestGroupingFactor.
	 * 
	 * @param cl
	 */
	private void handleRequestCloudlet(InternetCloudlet cl) {

		//Reflect completion of request transmission in traffic levels
		InternetCharacteristics.getInstance().removeTraffic((CommPath) cl.getData(Constants.PARAM_COMM_PATH), 
				cl.getRequestCount());

		int numOfActualRequests = cl.getRequestCount();
		int numOfReqCloudlets;
		if (numOfActualRequests <= requestsPerCloudlet){
			numOfReqCloudlets = 0;
		} else {
			numOfReqCloudlets = numOfActualRequests / requestsPerCloudlet;
		}

		int i;
		for (i = 0; i < numOfReqCloudlets; i++){
			InternetCloudlet subCloudlet = new InternetCloudlet(cl.getCloudletId() * 1000 + i,
					cl.getGridletLength() * requestsPerCloudlet,
					cl.getGridletFileSize() * requestsPerCloudlet,
					cl.getGridletOutputSize() * requestsPerCloudlet,
					cl.getOriginator(),
					cl.getAppId(),
					requestsPerCloudlet);
			subCloudlet.setParentId(cl.getCloudletId());
			submitNewCloudlet(subCloudlet);	
		}

		//If there are any remaining, which didn't fit into a default sized group
		int remainingRequests = numOfActualRequests - requestsPerCloudlet * numOfReqCloudlets;
		if (remainingRequests != 0){
			InternetCloudlet subCloudlet = new InternetCloudlet(cl.getCloudletId() * 1000 + i + 1,
					cl.getGridletLength() * remainingRequests,
					cl.getGridletFileSize() * remainingRequests,
					cl.getGridletOutputSize() * remainingRequests,
					cl.getOriginator(),
					cl.getAppId(),
					remainingRequests);
			subCloudlet.setParentId(cl.getCloudletId());
			submitNewCloudlet(subCloudlet);	
		}

		totalData += cl.getDataSize();
		long startTime = (long) GridSim.clock();

		//System.out.println(startTime + ": " + get_name() + " started processing " + cl.getCloudletId());
		processingCloudletStatuses.put(cl.getCloudletId(), new Long[]{(long) numOfActualRequests, 0L, startTime});		
	}

	private void cloudletCompletedProcessing(InternetCloudlet cl){
		//Notify load balancer of vm getting freed up
		int vmId = cl.getVmId();
		CloudSimEvent e = new CloudSimEvent(CloudSimEvents.EVENT_VM_FINISHED_CLOUDLET);
		e.addParameter(Constants.PARAM_VM_ID, vmId);
		fireCloudSimEvent(e);	

		submitWaitingCloudlet();
	}

	private void submitWaitingCloudlet(){
		int nextAvailVM = loadBalancer.getNextAvailableVm();

		if ((nextAvailVM != -1) && (waitingQueue.size() > 0)){
			InternetCloudlet cl = waitingQueue.remove(0);
			submitCloudlet(cl, nextAvailVM);
		}
	}

	private void submitNewCloudlet(InternetCloudlet cl) {

		hourlyArrival.addEvent(GridSim.clock(), cl.getRequestCount());
		if (loadBalancer == null){
			loadBalancer = new RoundRobinVmLoadBalancer(vmStatesList);
		}

		int nextAvailVM = loadBalancer.getNextAvailableVm();

		while(vmMapping[nextAvailVM]==-10){
			nextAvailVM = loadBalancer.getNextAvailableVm();
		}

		if (nextAvailVM == -1){
			//All VM's are busy. Put it in queue
			//System.out.println("VM's busy, queueing " + cl);
			waitingQueue.add(cl);	

			queuedCount++;
		} else {
			submitCloudlet(cl, nextAvailVM);
		}
	}


	private void submitCloudlet(InternetCloudlet cl, int vmId) {
		//submit to the next machine
		cl.setVmId(vmId);
		cl.setUserID(this.get_id());

		int dest = vmMapping[vmId];
		//System.out.println(GridSim.clock()+": "+this.get_name()+ ": Sending cloudlet "+cl.getGridletID()+" to VM #"+((VirtualMachine)vmlist.get(vmId)).getVmId() + ", dest=" + dest);
		super.send(dest,GridSimTags.SCHEDULE_NOW, GridSimTags.GRIDLET_SUBMIT, cl);

		cloudletsSubmitted++;

		//Notify load balancer
		CloudSimEvent e = new CloudSimEvent(CloudSimEvents.EVENT_CLOUDLET_ALLOCATED_TO_VM);
		e.addParameter(Constants.PARAM_VM_ID, vmId);
		fireCloudSimEvent(e);

		String destName = GridSim.getEntityName(dest);
		InternetEntitityRegistry.getInstance().addCommunicationPath(cl.getOriginator().get_name(), destName);
	}

	@Override
	protected void processVMCreate(Sim_event ev) {			
		int[] array = (int[]) ev.get_data();
		int vmId=array[1];	

		//If VM creation success
		if(array[2]==GridSimTags.TRUE){
			double vmStartTime = GridSim.clock();
			double vmEndTime = -1;

			vmUsage.put(vmId, new Double[]{vmStartTime, vmEndTime});
			vmStatesList.put(vmId, VirtualMachineState.AVAILABLE);

			//If there are any waiting cloudlets in queue, attempt to re-schedule them
			submitWaitingCloudlet();
		} else {
			//Don't try to create VM's further in this DC
			lastVmCreateFailed = true;
		}

		super.processVMCreate(ev);
	}

	public void createNewVm() {

		if (!lastVmCreateFailed){
			System.out.println("Trying to create vm");

			VMCharacteristics vm0Char = ((VirtualMachine)vmlist.get(0)).getCharacteristics();

			VMCharacteristics newVmChar = new VMCharacteristics(vmlist.size(),
					vm0Char.getUserId(),
					vm0Char.getSize(),
					vm0Char.getMemory(),
					vm0Char.getBw(),
					vm0Char.getCpus(),
					vm0Char.getPriority(),
					vm0Char.getVmm(),
					vm0Char.getVMScheduler());

			VirtualMachine newVm = new VirtualMachine(newVmChar);
			vmlist.add(newVm);

			int[] oldVmMapping = vmMapping;
			vmMapping = Arrays.copyOf(oldVmMapping, oldVmMapping.length + 1);

			//System.out.println(GridSim.clock()+": "+this.get_name()+ ": Trying to Create new VM # in " + GridSim.getEntityName(datacenterID[0]));

			super.send(datacenterID[0], GridSimTags.SCHEDULE_NOW, DatacenterTags.VM_CREATE_ACK, newVmChar);

			this.vmsRequested=1;
			this.vmsAcks=0;
		} 
	}


	/**
	 * @return the regionId
	 */
	public int getRegion() {
		return region;
	}

	public String getDataCenterName(){
		return get_name().substring(0, get_name().indexOf("-Broker"));
	}

	public double getTotalCost(){
		return getDataTransferCost() + getVmCost();
	}


	public double getDataTransferCost(){
		double dataGB = totalData / (1024 * 1024);
		return (dataGB * costPerDataGB);
	}

	public double getVmCost(){
		double totalTime = 0.0;
		double start, end;
		double now = GridSim.clock();

		for (Double[] vmAllocationTime : vmUsage.values()){
			start = vmAllocationTime[0];
			if (vmAllocationTime[1] == -1){
				end = now;
			} else {
				end = vmAllocationTime[1];
			}

			totalTime += (end - start);			
		}

		return ((totalTime / Constants.MILLI_SECONDS_TO_HOURS) * costPerVmHour);
	}


	/**
	 * @return the hourlyArrival
	 */
	public HourlyEventCounter getHourlyArrival() {
		return hourlyArrival;
	}


	public void addCloudSimEventListener(CloudSimEventListener l) {
		listeners.add(l);
	}


	public void fireCloudSimEvent(CloudSimEvent e) {
		for (CloudSimEventListener l : listeners){
			l.cloudSimEventFired(e);
		}
	}


	public void removeCloudSimEventListener(CloudSimEventListener l) {
		listeners.remove(l);
	}

	public Map<Integer, VirtualMachineState> getVmStatesList(){
		return vmStatesList;
	}


	/**
	 * @return the processingTimes
	 */
	public HourlyStat getHourlyProcessingTimes() {
		return hourlyProcessingTimes;
	}


	public Map<Integer, Integer> getVmAllocationStats(){
		if (loadBalancer != null){
			return loadBalancer.getVmAllocationCounts();
		} else {
			return null;
		}
	}


	/**
	 * @return the allRequestsProcessed
	 */
	public int getAllRequestsProcessed() {
		return allRequestsProcessed;
	}


}
