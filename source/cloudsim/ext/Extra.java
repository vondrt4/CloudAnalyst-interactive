package cloudsim.ext;

/**
 * This class implements a table of loads that the UserBase will offer every hour.
 * @author Juan
 *
 */
public class Extra {
	private double[][] load;
	private static double duration=24*3600000.0;
	private static double priceVMHour=0.1;
	public Extra() {
		super();
		
		load= new double[2][25];
		for (int i=0;i<25;i++){
			load[0][i] = i*Constants.MILLI_SECONDS_TO_HOURS;
			load[1][i] = 1082;
			//original values:32 and 1012
		}
/*
		load[1][0]=1000;
		load[1][1]=1200;
		load[1][2]=1000;
		load[1][3]=1300;
		load[1][4]=1500;
		load[1][5]=1800;
		load[1][6]=1700;
		load[1][7]=1800;
		load[1][8]=1900;
		load[1][9]=2200;
		load[1][10]=2000;
		load[1][11]=2600;
		load[1][12]=2900;
		load[1][13]=3200;
		load[1][14]=3200;
		load[1][15]=3600;
		load[1][16]=3000;
		load[1][17]=2200;
		load[1][18]=2300;
		load[1][19]=2000;
		load[1][20]=1600;
		load[1][21]=1300;
		load[1][22]=900;
		load[1][23]=800;
		load[1][24]=1200;
		load[1][25]=1000;
*/
	}

	public double[][] getLoad() {//For UserBase.getOnlineUsers
		return load;
	}

	public static double getDuration() {
		return duration;
	}
	
	public static double getpriceVMHour() {
		return priceVMHour;
	}
}
