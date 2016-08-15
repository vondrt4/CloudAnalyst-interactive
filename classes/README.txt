This directory contains examples in the use of CloudSim


To compile the example source codes:

    In Unix/Linux: javac -classpath $CLOUDSIM/jars/simjava2.jar:$CLOUDSIM/jars/gridsim.jar:$CLOUDSIM/jars/cloudsim.jar:. CloudSimExampleX.java
    In Windows:    javac -classpath %CLOUDSIM%/jars/simjava2.jar;%CLOUDSIM%\jars\gridsim.jar;%CLOUDSIM%\jars\cloudsim.jar;. CloudSimExampleX.java

To run the class file:
    In Unix/Linux:
        java -classpath $CLOUDSIM/jars/simjava2.jar:$CLOUDSIM/jars/gridsim.jar:$CLOUDSIM/jars/cloudsim.jar:. CloudSimExampleX > file.txt

    In Windows:
        java -classpath %CLOUDSIM%/jars/simjava2.jar;%CLOUDSIM%\jars\gridsim.jar;%CLOUDSIM%\jars\cloudsim.jar;. CloudSimExampleX > file.txt

The above command means run the program and output the results into a file
named "file.txt" rather than into screen or standard output.


Here are the summary of what each example does:

CloudSimExample1.java : shows how to create a datacenter with one host and run one cloudlet on it.

CloudSimExample2.java : shows how to create a datacenter with one host and run two cloudlets on it.
			The cloudlets run in VMs with different priorities.

CloudSimExample3.java : shows how to create a datacenter with two hosts and run two cloudlets on it.
			The cloudlets run in VMs with different priorities. However, since each VM
			run exclusively in each host, less priority will not delay the cloudlet on it.

CloudSimExample4.java : shows how to create two datacenters with one host each and run two cloudlets on them.

CloudSimExample5.java : shows how to create two datacenters with one host each and run cloudlets of two users on them. 

CloudSimExample6.java : shows how to create scalable simulations.

network: this directory contains examples on how to run simulation with network simulation.
