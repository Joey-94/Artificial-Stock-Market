package support;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import model.FinancialModel;
import model.agents.GenericPlayer;

import model.market.Asset;
import model.market.Market;
import model.market.books.OrderBook;

public class ModelFactory {

	public boolean returnSim = false;

	public FinancialModel target;
     
	public Vector<Double> informHouse= new Vector<Double>(); //wei 存储读入外部信息数据
	
	String fileName;
  
	public ModelFactory(FinancialModel target) {

		/* Read in logging settings. */

		if (target == null) {
			returnSim = true;
			target = new FinancialModel(System.currentTimeMillis());
		} else {
			this.target = target;
		}

		/* Read in simulation settings and set them to the target. */

		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream("setups//main.properties"));
		} catch (IOException e) {
		}

		
		target.parameterMap.put("maxT", new Double(properties.getProperty("maxT", "47999")));
		target.parameterMap.put("recordTime", new Double(properties.getProperty("recordTime", "36000")));
		target.parameterMap.put("numAssets", new Double(properties.getProperty("numAssets", "1")));
		target.parameterMap.put("initialPrice", new Double(properties.getProperty("initialPrice", "100")));
		target.parameterMap.put("initialPosition", new Double(properties.getProperty("initialPosition", "50")));
		
		target.parameterMap.put("lambda", new Double(properties.getProperty("lambda", "1")));
		target.parameterMap.put("quantity", new Double(properties.getProperty("quantity", "1")));
		target.parameterMap.put("granularity", new Double(properties.getProperty("granularity", "100")));
		target.parameterMap.put("beta", new Double(properties.getProperty("beta", "0.6")));
		target.parameterMap.put("alph", new Double(properties.getProperty("alph", "0.1")));
		target.parameterMap.put("phi", new Double(properties.getProperty("phi", "0.02")));
		target.parameterMap.put("sigma", new Double(properties.getProperty("sigma", "0.001")));
		target.parameterMap.put("mu", new Double(properties.getProperty("mu", "0")));
		target.parameterMap.put("gc", new Double(properties.getProperty("gc", "1.0")));
		
		target.optionsMap.put("logPricing", properties.getProperty("logPricing", "false"));
		target.parameterMap.put("hist", new Double(properties.getProperty("hist", "30")));
		target.parameterMap.put("tau", new Double(properties.getProperty("tau", "240")));
		target.parameterMap.put("tauf", new Double(properties.getProperty("tauf", "120")));
		target.parameterMap.put("tauSTD", new Double(properties.getProperty("tauSTD", "20")));
		target.parameterMap.put("T", new Double(properties.getProperty("T", "240")));
		target.parameterMap.put("k", new Double(properties.getProperty("k", "100")));
		target.parameterMap.put("cost", new Double(properties.getProperty("cost", "0.004")));
		target.parameterMap.put("displayTime", new Double(properties.getProperty("displayTime", "10")));
		
		target.parameterMap.put("sigma1", new Double(properties.getProperty("sigma1", "10")));
		target.parameterMap.put("sigma2", new Double(properties.getProperty("sigma2", "0.2")));
		target.parameterMap.put("sigman", new Double(properties.getProperty("sigman", "1")));
		target.parameterMap.put("sigmae", new Double(properties.getProperty("sigmae", "0.001")));

		target.parameterMap.put("cost", new Double(properties.getProperty("cost", "0.004")));

		
		target.optionsMap.put("agentConfiguration", properties.getProperty("agentConfiguration", "chsw.txt"));
		target.optionsMap.put("orderBookClass", properties.getProperty("orderBookClass", "DoubleAuctionOrderBook"));
		target.optionsMap.put("orderBookOptions", properties.getProperty("orderBookOptions", "logPricing"));
	
	}

	public void buildAgents() {

		//初始化资产
	
		target.myAsset = new Asset(target);
		target.schedule.scheduleRepeating(target.myAsset, 2, 1.0);

		
		/**读入数据作为基本价值
		try {
			in = new BufferedReader(new FileReader("setups//" +"data.txt"));
			
			while (!((tempLine = in.readLine()) == null)) {
				informHouse.add(Double.parseDouble(tempLine));
			}
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		**/
		
		// initialize an array list of traders
		target.agentList = new ArrayList<GenericPlayer>();
		
		BufferedReader in;
		String tempLine;
		StringTokenizer st;
		
		
		try {
			in = new BufferedReader(new FileReader("setups//" + target.optionsMap.get("agentConfiguration")));
			while (!((tempLine = in.readLine()) == null)) {
				st = new StringTokenizer(tempLine, ",");
				String className = st.nextToken();
				int numOfInstances = new Integer(st.nextToken()).intValue();			
				this.createLotsOfAgents(className, numOfInstances);
			}
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// initialize market agent
		target.myMarket = new Market(target);

		try {
			for (int a = 0; a < target.parameterMap.get("numAssets"); a++) {
				target.myMarket.orderBooks.add((OrderBook) Class.forName("model.market.books." + target.optionsMap.get("orderBookClass")).newInstance());
			}
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		for (int a = 0; a < target.parameterMap.get("numAssets"); a++) {
			target.myMarket.orderBooks.get(a).setMyWorld(target);
			target.myMarket.orderBooks.get(a).setMyID(a);
		}

		target.schedule.scheduleRepeating(target.myMarket, 2, 1.0);

	}

	private void createLotsOfAgents(String className, int numOfInstances) {
		
		
		try {

			// initialize traders and add them to the list
			
			for (int i = 0; i < numOfInstances; i++) {
				// create new trader
				GenericPlayer tempAgent = null;
				tempAgent = (GenericPlayer) Class.forName("model.agents." + className).newInstance();
				// setup agent
				//int tau=0;
				//int hist=0;
				/*
				if(i<13){tau=5;hist=36;}
				else if(13<=i&&i<29){tau=15;hist=24;}
				else if(29<=i&&i<49){tau=30;hist=24;}
				else if(49<=i&&i<89){tau=60;hist=30;}
				else if(89<=i&&i<295){tau=180;hist=20;}
				else{tau=360;hist=20;}*/
				/*
				if(i<3){tau=1;hist=60;}
				else if(3<=i&&i<18){tau=5;hist=48;}
				else if(18<=i&&i<63){tau=15;hist=32;}
				else if(63<=i&&i<153){tau=30;hist=24;}
				else if(153<=i&&i<333){tau=60;hist=20;}
				else{tau=240;hist=20;}*/
				tempAgent.setup(i,target);
				// add trader to the list
				target.agentList.add(tempAgent);
			}

		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
