package model.market;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Vector;

import model.FinancialModel;
import model.agents.HBPlayer;
import model.agents.GenericPlayer;

import model.market.books.LimitOrder;
import model.market.books.LiquidityException;
import model.market.books.OrderBook;
import model.market.books.OrderBook.OrderType;
import sim.engine.SimState;
import sim.engine.Steppable;

/* Class: Market 
 * Spring 2008
 * FinancialMarketModel Team
 * 
 * Function: generates the market price based on 
 * the orders placed by traders 
 */

public class Market implements Steppable {

	public FinancialModel myWorld;
	
	private double granularity = 0.0; // round to tenths, hundredths, thousandths, etc
	
	
	private int maxTau;//longest investment horizon
	private double T;
	public boolean bos;//last market order buy or sell;
	public double tp=0;// use to caculate average price;
	public double ap;
	private double time;//get current time
	public  double avIR=0.0;//Informed交易者的平均收益 
	public  double avUR=0.0;//Ga交易者的平均收益 
	
	
	public double earnings = 0.0;
	public double realEarnings = 0.0;
    public double aggregateAlpha = 0.0;
    public boolean newEarnings = false; // 上市公司是否发布了新的盈利
    public boolean failEM = false; // 上市公司时候盈余管理失败
    public boolean earningsManagement = false; // 上市公司是否进行了盈余管理
	
	
	public int countILBB=0;
	public int countILBA=0;
	public int countILBU=0;
	public int countIMB=0;
	public int countILSU=0;
	public int countILSA=0;
	public int countILSB=0;
	public int countIMS=0;
	
	
	public int countULBB=0;
	public int countULBA=0;
	public int countULBU=0;
	public int countUMB=0;
	public int countULSU=0;
	public int countULSA=0;
	public int countULSB=0;
	public int countUMS=0;
	public double pEM = 0;
	public double pTrue = 0;
	
	
	public double gf=0;
	
	public double wf=0;//wealth
	
	public double wbsv=0;//wealth
	public double wn=0;//wealth
		
	
	public int countBMO=0;
	public int BMOQuantity=0;
	public int countBLO=0;
	public int BLOQuantity=0;
	
	public int countSMO=0;
	public int SMOQuantity=0;
	public int countSLO=0;
	public int SLOQuantity=0;
	
	
	public  int countIT=0;//informed trader's executive limit order
	public  int countUT=0;//uninformed trader's executive limit order
	public double tickSize=0.01;
    int asset;
    
    public double maxPrice; //涨幅
    public double minPrice; //跌幅 
    public double range=0.1;//涨跌幅大小
    public double recordTime;
    
    public Vector<Double> askP = new Vector<Double>();//by WEI，存储历史价格
	public Vector<Double> bidP = new Vector<Double>();//by WEI，存储历史价格
    public Vector<Integer> da = new Vector();//by WEI，two periods depth at ask
	public Vector<Integer> db = new Vector();//by WEI，two periods depth at bid
	public Vector<Integer> daa = new Vector();//by WEI，two periods depth away ask
	public Vector<Integer> dbb = new Vector();//by WEI，two periods depth below ask
	
    public int bitNum=10;
	public int[] gaBitUsed;
	public int orderMark;
	
	public int[][] orderCorrellation;
    public double tauSTD;
	// ArrayList of orderBooks (one for each of the assets)
	public ArrayList<OrderBook> orderBooks = new ArrayList<OrderBook>();
	public Vector<Double> priceHistory = new Vector<Double>();//by WEI，存储历史价格
	public Vector<Double> volumeHistory = new Vector<Double>();//by WEI，recording history volume
	// constructor
	public Market(FinancialModel myWorld) {
		this.myWorld = myWorld;
		tauSTD=myWorld.parameterMap.get("tauSTD");
		maxTau=myWorld.parameterMap.get("tau").intValue();
		this.T=myWorld.parameterMap.get("T");
		this.recordTime=myWorld.parameterMap.get("recordTime");
	//	priceHistory.add(myWorld.myCreator.informHouse.elementAt(0));//需要赋予初始值，以便别的agent调用
		priceHistory.add(myWorld.parameterMap.get("initialPrice"));
		volumeHistory.add(3.0);
		granularity = myWorld.parameterMap.get("granularity");
		this.tickSize=1/granularity;
		asset=0;
		this.time=myWorld.schedule.getTime();
		askP.add(myWorld.parameterMap.get("initialPrice"));
		bidP.add(myWorld.parameterMap.get("initialPrice"));
		da.add(0);
		db.add(0);
		daa.add(0);
		dbb.add(0);
		gaBitUsed=new int[bitNum];
		orderMark=4;
		orderCorrellation=new int[orderMark][orderMark];
/*      
		for (int o = 0; o < myWorld.parameterMap.get("numAssets"); o++) {

		}
*/

	}

	// function contains the main business logic of the class
	// generates return rate and new price
	public void step(SimState state) {
		
		for (OrderBook b : this.orderBooks) {
			b.cleanup();
		}
		
		this.time=myWorld.schedule.getTime();
		CaculateAP();
		if(time==0){//add new value into vector, contain two value;
			askP.add(myWorld.myMarket.getAskPriceForAsset(0));
			bidP.add(myWorld.myMarket.getBidPriceForAsset(0));
			da.add(myWorld.myMarket.getAskQuantityForAsset(0));
			db.add(myWorld.myMarket.getBidQuantityForAsset(0));
			daa.add(myWorld.myMarket.getDepthAboveAskForAsset(0));
			dbb.add(myWorld.myMarket.getDepthBelowBidForAsset(0));	
			
		}
		if(time>= 4800){
			priceHistory.remove(0);//只记录最近tau期的价格
		    volumeHistory.remove(0);
		}
		if(time!=0){//注意0期已经赋予过初始价格了，不再重复，从第1期开始记录，也方便访问。
			priceHistory.add(getTPriceForAsset(asset));
		    volumeHistory.add(getVolumeForAsset(asset));
		   
			askP.remove(0);//The initial value and O period value already exits,so remove one value and then add a new value
			askP.add(myWorld.myMarket.getAskPriceForAsset(0));
			bidP.remove(0);
			bidP.add(myWorld.myMarket.getBidPriceForAsset(0));
			da.remove(0);
			da.add(myWorld.myMarket.getAskQuantityForAsset(0));
			db.remove(0);
			db.add(myWorld.myMarket.getBidQuantityForAsset(0));
			daa.remove(0);
			daa.add(myWorld.myMarket.getDepthAboveAskForAsset(0));
			dbb.remove(0);
			dbb.add(myWorld.myMarket.getDepthBelowBidForAsset(0));	
		}
		 // 计算涨跌幅限制
         
  	//	if((time%240==0)){
  	//		maxPrice=(1+range)*this.getTPriceForAsset(asset);
  	//	    minPrice=(1-range)*this.getTPriceForAsset(asset);
  	//	}
		
				
		if(time==(recordTime-1)){//将所有参数重新设为0
			avIR=0.0;//Informed交易者的平均收益 
			avUR=0.0;//Ga交易者的平均收益 
			countILBB=0;
			countILBA=0;
			countILBU=0;
			countIMB=0;
			countILSU=0;
			countILSA=0;
			countILSB=0;
			countIMS=0;
			
			
			countULBB=0;
			countULBA=0;
			countULBU=0;
			countUMB=0;
			countULSU=0;
			countULSA=0;
			countULSB=0;
			countUMS=0;
			
			countIT=0;//
			countUT=0;//
			
			this.countBLO=0;
			this.countBMO=0;
			this.countSLO=0;
			this.countSMO=0;
			this.BLOQuantity=0;
			this.BMOQuantity=0;
			this.SLOQuantity=0;
			this.SMOQuantity=0;
			
			for(int i=0;i<bitNum;i++){
				gaBitUsed[i]=0;
			}
			
			
			for(int i=0;i<orderMark;i++){
				for(int j=0;j<orderMark;j++){
					this.orderCorrellation[i][j]=0;
				}
			}
		}
	
	this.caculateWealth();	
	
	}

	public double getReturnRateForAsset(int i) {
		return this.orderBooks.get(i).getReturnRate();
	}

	public OrderBook getOrderBookForAsset(int i) {
		return orderBooks.get(i);
	}

	
	public double getAskPriceForAsset(int i) {
		return orderBooks.get(i).getAskPrice();
	}

	public boolean acceptOrder(LimitOrder tempOrder,int orderMark) {
		return orderBooks.get(tempOrder.assetID).placeLimitOrder(tempOrder,orderMark);		
	}
	
	public double getSpreadForAsset(int i){
		return orderBooks.get(i).getSpread();
	}
	
	public int getSPForAsset(int i){// get spread in ticksize
		return (int) (this.getSpreadForAsset(i)/this.tickSize);
	}


	public boolean cancelOrder(LimitOrder tempOrder) {
		return orderBooks.get(tempOrder.assetID).cancelLimitOrder(tempOrder);		
	}
	
	
	public double getBidPriceForAsset(int i) {
		return orderBooks.get(i).getBidPrice();
	}
	
	public void acceptMarketOrder(GenericPlayer investor,String ruleID,OrderType newType, double price, int asset, int amount,int orderMark) {
		try {
			this.orderBooks.get(asset).executeMarketOrder(investor,ruleID,newType, price, amount,orderMark);
		} catch (LiquidityException e) {
			// TODO: this should be left for the caller to catch
			 e.printStackTrace();
		}
		
	}


	public double getAverageTradePriceForAsset(int i) {
		return orderBooks.get(i).getAverageTradePrice();
	}

	public double getVolumeForAsset(int i) {
		return orderBooks.get(i).getVolume();
	}
	public double getTPriceForAsset(int i) {
		return orderBooks.get(i).getTPrice();
	}
	public double getLastTradePriceForAsset(int i) {
		return orderBooks.get(i).getLastTradePrice();
	}
	public double getPmForAsset(int i) {
		return orderBooks.get(i).getPm();
	}
	public int getBookStateForAsset(int i) {
		return orderBooks.get(i).getBookState();
	}
	//获取历史价格
	public double getPriceHistoryForAsset(int i) {
		return priceHistory.get(i);
	}
	
	public double getVolumeHistoryForAsset(int i) {
		return volumeHistory.get(i);
	}
	//计算特定时段的平均价格 
	public void CaculateAP(){
		double t;
		if(time%maxTau==0)//例如tau=100,则第100期开始，tp=0;
			this.tp=0;
		t=(time+1)%maxTau;
		this.tp+=getTPriceForAsset(0);
		this.ap=roundPrice(tp/t);
	}
	public double getAveragePrice(){
	/*
		double ap=0; 
		int i=0;
		if(time<tau){
			if(time==0){
				return ap=getPriceHistoryForAsset(0);
			}else{
				for(i=0;i<time;i++){
					ap+=getPriceHistoryForAsset(i);
				}
				return ap=roundDouble(ap/(time));
			}
	}else{
		double  rt=(time+1)%T;
		if(rt==0){
			return ap=getPriceHistoryForAsset(tau-1);
		}else{
			for(i=1;i<=rt;i++){
				ap+=getPriceHistoryForAsset(tau-i);//最近的在末尾
			}
			return ap=roundDouble(ap/(rt));
		}
		} */
		
		return this.ap;

	}
	
	
	//获取信息
	/*
	public double getInformValueForAsset(int i) {
		int time=(int)this.myWorld.schedule.getTime();
		return roundDouble(myWorld.myCreator.informHouse.elementAt(time));	
	}
	*/	
	//取小数点后两位，4舍5入
	public double roundDouble(double num) {
   	  BigDecimal   bd   =   new   BigDecimal(num);   
		  bd   =   bd.setScale(2,BigDecimal.ROUND_HALF_UP);   
		  num=bd.doubleValue();
 		  return num;
 	  
 	}
	

/*	
	public  double getAvIR(){
		if((this.countIT+this.countIMO)>0)
		 	return   this.avIR/(this.countIT+this.countIMO);
		else return 0.0;
	}//Informed交易者的平均收益 
	
	public  double getAvGAR(){
		if((this.countGAT+this.countGAMO)>0)
		 	return   this.avGAR/(this.countGAT+this.countGAMO);
		else return 0.0;
	};//Ga交易者的平均收益 
	
	
	public  int getCountILO(){
		return this.countILO;
	}//记录用Informed下限价单的次数
	public  int getCountIALO(){
		return this.countIALO;
	}
	public  int getCountIULO(){
		return this.countIULO;
	}
	public  int getCountGALO(){
		return this.countGALO;
	}//记录用GA下限价单的次数
	
	public  int getCountGAALO(){
		return this.countGAALO;
	}//记录用GA下限价单的次数
	
	public  int getCountGAULO(){
		return this.countGAULO;
	}//记录用GA下限价单的次数
	
	public  int getCountIMO(){
		return this.countIMO;
	}//记录用Informed下市价单的次数
	public  int getCountGAMO(){
		return this.countGAMO;
	}//记录用GA下市价单的次数
	
	public  int getCountIT(){
		return this.countIT;
	}//记录用Informed限价单成交的次数
	public  int getCountGAT(){
		return this.countGAT;
	}//记录用GA限价单成交的次数
	*/
	
	public  double getAvIR(){
		if((this.countIT+this.countIMB+this.countIMS)>0)
		 	return   this.avIR/(this.countIT+this.countIMB+this.countIMS);
		else return 0.0;
	}//Informed交易者的平均收益 
	
	public  double getAvUR(){
		if((this.countUT+this.countUMB+this.countUMS)>0)
		 	return   this.avUR/(this.countUT+this.countUMB+this.countUMS);
		else return 0.0;
	};//
	
	// informed trader , buy
	public  int getCountILBB(){
		return this.countILBB;
	}//
	public  int getCountILBA(){
		return this.countILBA;
	}
	public  int getCountILBU(){
		return this.countILBU;
	}
	public  int getCountIMB(){
		return this.countIMB;
	}
	//informed trader, sell
	public  int getCountILSB(){
		return this.countILSB;
	}
	public  int getCountILSA(){
		return this.countILSA;
	}
	public  int getCountILSU(){
		return this.countILSU;
	}
		
	public  int getCountIMS(){
		return this.countIMS;
	}
	
	public  int getCountIT(){
		return this.countIT;
	}//记录用Informed限价单成交的次数
	public  int getCountUT(){
		return this.countUT;
	}//记录用GA限价单成交的次数
	
	
	
	// uninformed trader , buy
		public  int getCountULBB(){
			return this.countULBB;
		}//
		public  int getCountULBA(){
			return this.countULBA;
		}
		public  int getCountULBU(){
			return this.countULBU;
		}
		public  int getCountUMB(){
			return this.countUMB;
		}
		//uninformed trader, sell
		public  int getCountULSB(){
			return this.countULSB;
		}
		public  int getCountULSA(){
			return this.countULSA;
		}
		public  int getCountULSU(){
			return this.countULSU;
		}
			
		public  int getCountUMS(){
			return this.countUMS;
		}
		

	
	
	
	
	
	public int getBidQuantityForAsset(int i) {
		return orderBooks.get(i).getBidQuantity();	
}
    public int getAskQuantityForAsset(int i) {
	return orderBooks.get(i).getAskQuantity();
}
    public int getDepthAboveAskForAsset(int i) {
    	return orderBooks.get(i).getDepthAboveAsk();
    }
    public int getDepthBelowBidForAsset(int i) {
        	return orderBooks.get(i).getDepthBelowBid();
      }
    public boolean getBuyorSellForAsset(int i) {
        	return this.bos;
     }
    public int[] getSellDepthForAsset(int i) {
    	return orderBooks.get(i).caulateSellDepth();
  }
    public int[] getBuyDepthForAsset(int i) {
    	return orderBooks.get(i).caulateBuyDepth();
  }
    
    public int[][] getOrderCorrelation(){
    	return this.orderCorrellation;
  }
      
    
    
   //获取涨幅
    public double getMaxPrice(int i){
    	return roundPrice(this.maxPrice);
    }
    //获取跌幅
    public double getMinPrice(int i){
    	return roundPrice(this.minPrice);
    }
  
    public double roundPrice(double price) {
		  if (granularity == 0.0) {
			  return price;
		  } else {
			  return ((double)((int)(price * granularity + 0.5)))/granularity;
		  }
		}
    
    public double getTradingProfitOfAgent(int agentID){
    	double profit=0;
    	return profit;
    	
    }
    public int getBosValue(){
    	int temp=0;
    	if(this.bos)
    		temp=0;
    	else 
    		temp=1;
    	return temp;
    }
    
    public void updateGaBitUsed(String conditions){
    	for(int n=0;n<conditions.length();n++){
			  	if(!conditions.substring(n,n+1).equals("2")){
			  		this.gaBitUsed[n]+=1;
			  	}
        }
    	
    }
    
    public int[] getGaBitUsed(){
    	return this.gaBitUsed;
    }
   
    
   public void caculateWealth(){
	   this.wf=0.0;
	   
	   this.wbsv=0.0;
	   this.wn=0.0;
	   for(int i=0;i<myWorld.agentList.size();i++){
		   GenericPlayer agent=myWorld.agentList.get(i);
		   if(agent.type.equals("FPlayer")){
		        this.wf+=agent.wealth;
		   }
		   if(agent.type.equals("BSVPlayer")){
		        this.wbsv+=agent.wealth;
		   }
		   if(agent.type.equals("NPlayer")){
		        this.wn+=agent.wealth;
		   }
	   }
   }  
   
   public ArrayList<Double> getPastPrices(int length){
	   ArrayList<Double> pastPrices = new ArrayList<Double>();
	   double temp = 0.0;
	   int historyLimit = this.priceHistory.size();
	   
	   if (historyLimit < length) {
		   System.out.println("The length of historical prices is shorter than the window requested");
	   }
	   for (int i = 0; i < length; i++) {
		   int at = historyLimit - (length - i);
		   temp = this.priceHistory.get(at);
		   pastPrices.add(temp);
	   }
	
	   return pastPrices;
   }
   
   public void updateEarnings(double earnings, double realEarnings, boolean em) {
	   this.earnings = earnings;
	   this.realEarnings = realEarnings;
	   this.earningsManagement = em;
	   this.newEarnings = true;
   }
 
}

