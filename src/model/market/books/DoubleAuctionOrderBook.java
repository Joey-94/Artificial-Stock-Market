/**
 *  DoubleAuctionOrderBook implementation
 */
package model.market.books;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

import model.FinancialModel;
import model.agents.HBPlayer;
import model.agents.GenericPlayer;
import model.market.books.OrderBook.OrderType;

/**
 * @author jbriggs Implements a double auction order book. The market moves when
 *         market orders fulfill limit orders. - limit orders don't ever execute
 *         when they're placed
 * @author Revised by Lijian Wei
 * 
 * 
 */
public class DoubleAuctionOrderBook implements OrderBook {

	protected FinancialModel myWorld;

	protected SortedSet<LimitOrder> buyOrders;

	protected SortedSet<LimitOrder> sellOrders;

	protected int myID;

	public double returnRate_t = 0;

	public double price_t;// the  price in every transaction
	public double tradePrice;//average trading price in time t
	

	// set to true if price as stored in the order book are the log of actual
	// price;
	// this implies negative prices are perfectly acceptable
	public boolean logPricing = false;

	public double volumeTraded = 0;
	public double valueTraded = 0;
	public double oldVolumeTraded = 0;//t时刻成交量
	public double oldValueTraded = 0;//t时刻成交总金额
	
    private int lastOrderMark=-1;
    
   
	public DoubleAuctionOrderBook() {
		super();
		
		this.buyOrders = new TreeSet<LimitOrder>();
		this.sellOrders = new TreeSet<LimitOrder>();

		
	}

	public synchronized boolean placeLimitOrder(LimitOrder order, int orderMark) {
		if (order.quantity <= 0 || order.assetID != myID) {
			return false;
		}

		// give it a new, unique transaction ID within the orderbook
		// order.transactionID.set(nextTransactionID++);

		if (order.type == OrderType.PURCHASE) {
			buyOrders.add(order);
			myWorld.myMarket.bos=true;
		} else {
			sellOrders.add(order);
			myWorld.myMarket.bos=false;
		}
		setOrderCorrelation(orderMark);
		return true;
	}

	// Attempt to cancel a given limit order.
	// Returns true on successful cancellation with no units transacted.
	// Returns false otherwise; final status can be found in order.
	public synchronized boolean cancelLimitOrder(LimitOrder order) {

		order.cancelled.set(true);
		if (order.type == OrderType.PURCHASE) {
			buyOrders.remove(order);
		} else {
			sellOrders.remove(order);
		}

		return true;
	}

	// Returns total price of purchasing 'quantity' units if successful.
	// Otherwise, throws LiquidityException, which contains the number
	// successfully executed.
	public synchronized double executeMarketOrder(GenericPlayer investor,String ruleID, OrderType type, double price, int quantity,int orderMark) throws LiquidityException {
		double currentTime = myWorld.schedule.getTime();
		double cv=myWorld.myAsset.getCommonValue();
		setOrderCorrelation(orderMark);
		// The operation is the same regardless of whether we're buying or
		// selling. We just need to choose the right orders to work on.
		SortedSet<LimitOrder> orders;
		boolean exec=true;// executed price should in the give price level
		if (type == OrderType.PURCHASE) {
			orders = sellOrders;
			
		} else {
			orders = buyOrders;
		}

		while ((quantity > 0) && (!orders.isEmpty())&& exec) {

			
			LimitOrder lo = orders.first();
			if(type==OrderType.PURCHASE){
				if(orders.first().pricePerUnit>price){// executed price should no more than the given price;
					exec=false;
				}
			}else{
				if(orders.first().pricePerUnit<price){//executed sell price should no less than the given price;
					exec=false;
				}
				
			}			


			int curQuantity = 0;
			if (lo.quantityPending() >quantity) {
				curQuantity = quantity;
			} else {
				//curQuantity = quantity - lo.quantityPending();
				curQuantity = lo.quantityPending();
				
			}
			price_t=lo.pricePerUnit;
			quantity -= curQuantity;
			caculateLimitOrderTransaction(lo,curQuantity,cv);
			caculateMarketOrder(investor,type,ruleID, curQuantity,cv,price_t);
			// Execute:
			// Update the LimitOrder by adding to the quantityExecuted
			lo.quantityExecuted.addAndGet(curQuantity);
			// Update the market order
			
            // Update aggregate value/volume traded
			this.valueTraded += lo.pricePerUnit * curQuantity;//t时刻成交总金量
			this.volumeTraded += curQuantity;//t时刻成交总量

			if (lo.quantityPending() == 0) {
				// the limitOrder is fully executed; remove it from the pending  orders
				
				orders.remove(lo);
			}
		}

	   
    		if (quantity > 0) { //the rest quantity change to limit orders
    			 
    			 investor.submitLimitOrder(type, 0, price, quantity);
    		}
		return price_t;
	}

	public synchronized double getBidPrice() {

		double bid;
		if (buyOrders.isEmpty()) {
			// TODO: this should probably change to price_t
			return this.price_t;
			
			/*
				bid=myWorld.myMarket.roundPrice(0.99*this.price_t);
			     if(bid<myWorld.myMarket.tickSize)
					bid=myWorld.myMarket.tickSize;
				return bid; */
			
		} else {
			return buyOrders.first().pricePerUnit;
		}

	}

	public synchronized double getAskPrice() {

		double ask;
		if (sellOrders.isEmpty()) {
			// TODO: this should probably change to price_t
				return this.price_t;
			/*
				ask=myWorld.myMarket.roundPrice(1.01*this.price_t);
			     if(ask<2*myWorld.myMarket.tickSize)
					ask=2*myWorld.myMarket.tickSize;
				return ask;*/
			
		} else {
			return sellOrders.first().pricePerUnit;
		}

	}

	public synchronized double getSpread() {
		
		return this.getAskPrice()-this.getBidPrice();
	}

	public synchronized void cleanup() {

		/* Clean expired orders */

		HashSet<LimitOrder> ordersToRemove = new HashSet<LimitOrder>();
		double currentTime = myWorld.schedule.getTime();
        
		for (LimitOrder l : this.sellOrders) {
			if (l.expirationTime <= currentTime) {
				ordersToRemove.add(l);
			}
		}
		sellOrders.removeAll(ordersToRemove);
		ordersToRemove.clear();

		for (LimitOrder l : this.buyOrders) {
			if (l.expirationTime <= currentTime) {
				ordersToRemove.add(l);
			}
		}
		buyOrders.removeAll(ordersToRemove);
		
		/* Clean up negative spreads by trading overlapping LimitOrders, so that total executed limit orders are more than total market orders */
		while ((this.getSpread() <= 0.0) && (buyOrders.size() > 0) && (sellOrders.size() > 0)) {

			LimitOrder firstBuy = buyOrders.first();
			LimitOrder firstSell = sellOrders.first();
			double cv=myWorld.myAsset.getCommonValue();
			int curQuantity = Math.min(firstBuy.quantityPending(), firstSell.quantityPending());
			firstBuy.quantityExecuted.addAndGet(curQuantity);
			firstSell.quantityExecuted.addAndGet(curQuantity);
			// trading price is equal to the midprice
		    this.price_t=myWorld.myMarket.roundPrice((firstSell.pricePerUnit+firstBuy.pricePerUnit)/2);
		    this.valueTraded+=price_t*curQuantity;
		    this.volumeTraded+=curQuantity;
		    caculateLimitOrderTransaction(firstBuy,curQuantity,cv);
		    caculateLimitOrderTransaction(firstSell,curQuantity,cv);
		   	if (firstBuy.quantityPending() == 0) {
				buyOrders.remove(firstBuy);
			}

			if (firstSell.quantityPending() == 0) {
				sellOrders.remove(firstSell);
			}
			

		}

		/* Calculate return rate  current time ,last transaction time；*/
		//double newPrice_t = (getAskPrice() + getBidPrice()) / 2;
		double oldPrice_t =myWorld.myMarket.getPriceHistoryForAsset(0);
		if (logPricing) {
			returnRate_t = oldPrice_t-price_t;
		} else {
			// note we must be careful here to use this only
			// when price_t cannot be 0, and the ratio can only be positive.
			returnRate_t = Math.log(price_t / oldPrice_t);
		}
		
		this.oldValueTraded= this.valueTraded;
		this.valueTraded = 0;
		this.oldVolumeTraded = this.volumeTraded;
		this.volumeTraded = 0;
		
	}

	// returns an array with an entry of the price for each unit of limit order
	public double[] getBuyOrders() {
		Vector<Double> freqVec = new Vector<Double>();

		// Iterate through limit order queues from askPrice on up
		for (LimitOrder l : buyOrders) {

			// theoretically this should be quantityPending
			for (int i = 0; i < l.quantity; i++) {
				freqVec.add(l.pricePerUnit);
			}

		}

		double[] retArray = new double[freqVec.size()];
		for (int i = 0; i < freqVec.size(); i++) {
			retArray[i] = freqVec.get(i);
		}

		return retArray;
	}

	// returns an array with an entry of the price for each unit of each limit
	// order
	public double[] getSellOrders() {
		Vector<Double> freqVec = new Vector<Double>();

		// Iterate through limit order queues from askPrice on up
		for (LimitOrder l : sellOrders) {

			// theoretically this should be quantityPending
			for (int i = 0; i < l.quantity; i++) {
				freqVec.add(l.pricePerUnit);
			}

		}

		double[] retArray = new double[freqVec.size()];
		for (int i = 0; i < freqVec.size(); i++) {
			retArray[i] = freqVec.get(i);
		}
		return retArray;
	}

	public synchronized void setMyWorld(FinancialModel myWorld) {
		this.myWorld = myWorld;
		this.price_t=this.tradePrice = myWorld.parameterMap.get("initialPrice");
		if (this.myWorld.optionsMap.get("orderBookOptions").equalsIgnoreCase("logPricing")) {
			logPricing = true;
		}
	}

	public double getReturnRate() {
		return returnRate_t;
	}

	public double getTickPrice() {
		if(price_t<myWorld.myMarket.tickSize)
			price_t=myWorld.myMarket.tickSize;
		return price_t;
	}

	public void setMyID(int a) {
		this.myID = a;
	}


	public double getVolume() {
		return this.oldVolumeTraded;
	}

	public double getAverageTradePrice() {
		//如果无成交，则为0；
		if(this.oldVolumeTraded!=0)
		     this.tradePrice=roundDouble(this.oldValueTraded / this.oldVolumeTraded) ;
		
		//else no transaction in time t, keep the same;
		return this.tradePrice;
	
	}
	
	//取小数点后两位，4舍5入
	public double roundDouble(double num) {
   	  BigDecimal   bd   =   new   BigDecimal(num);   
		  bd   =   bd.setScale(2,BigDecimal.ROUND_HALF_UP);   
		  num=bd.doubleValue();
 		  return num;
 	  
 	}
	
	
	public double getTPrice() {
			
		//use the the last price of period T as the TPrice
		if(price_t<myWorld.myMarket.tickSize)
			price_t=myWorld.myMarket.tickSize;
		return this.price_t;	
	}
	

	public double getLastTradePrice() {
		if(price_t<myWorld.myMarket.tickSize)
			price_t=myWorld.myMarket.tickSize;
		return this.price_t;
	}
	public double getPm() {
		return roundDouble((this.getAskPrice()+this.getBidPrice())/2);
	}
	public int getBookState() {
		int s=0;
		if(this.sellOrders.isEmpty())//卖订单簿为空 
			s=1;
		if(this.buyOrders.isEmpty())//买订单簿为空
			s=2;
		if(this.sellOrders.isEmpty()&&this.buyOrders.isEmpty())
			s=3;
		return s;
	}
	
	public int getBidQuantity(){
	    double[] tempArray = this.getBuyOrders();
	    int bq=0;
	    int i=tempArray.length;
	    int j=0;
	    if(i>0){
			while(tempArray[0]==tempArray[j]){
					j++;
					bq++;
					if(j>=i)
						break;
				
			}
		}
	    return bq;
		   
   }
	public int getDepthBelowBid(){
	    double[] tempArray = this.getBuyOrders();
	    int dbb=0;
	    int i=tempArray.length;
	    int j=getBidQuantity();
	    dbb=i-j;
	    return dbb;
		   
   }
	
	
   
   public int getAskQuantity(){
	    double[] tempArray = this.getSellOrders();
	    int sq=0;
	    int i=tempArray.length;
	    int j=0;
	    if(i>0){
			while(tempArray[0]==tempArray[j]){
					j++;
					sq++;
					if(j>=i)
						break;
				
			}
		}
	    return sq;
		   
  }
   
   public int getDepthAboveAsk(){
	    double[] tempArray = this.getSellOrders();
	    int daa=0;
	    int i=tempArray.length;
	    int j=getAskQuantity();
	    daa=i-j;
	    return daa;
		   
 }
	

   private void caculateMarketOrder(GenericPlayer investor,OrderType type,String rid, int pos, double cv,double p){
	  
	 //  investor.updatewealth(p, pos, type);
	   
	 if(!investor.type.equals("LPlayer")){
	   if(type.equals(OrderType.PURCHASE)){
			investor.position+=pos;
			investor.cash-=pos*p;
			investor.wealth=investor.position*p+investor.cash;
		}else{
			investor.position-=pos;
			investor.cash+=pos*p;
			investor.wealth=investor.position*p+investor.cash;
		}
	 }
	//   System.out.println("cacuMarket!!!");
	   /*
	   int atime=(int)(myWorld.schedule.getTime());
	   double profit=0;
	   if(type==OrderType.SALE)
		     profit=p-cv;
	   else 
		   profit=cv-p;
	   
	   investor.updateTradingRecord(rid, atime, profit);// Feedback the orderProfit to investor 
	  
		   if(investor.type=="IPlayer"){//知情
			   
			   if(type==OrderType.PURCHASE)
				   myWorld.myMarket.countIMB+=totalQuantity;
			   else
				   myWorld.myMarket.countIMS+=totalQuantity;
			   
			   myWorld.myMarket.avIR+=profit*totalQuantity;
			
		   }else{//GA
				
				if(type==OrderType.PURCHASE)
					myWorld.myMarket.countUMB+=totalQuantity;
			    else
			    	myWorld.myMarket.countUMS+=totalQuantity;
				
				myWorld.myMarket.avUR+=profit*totalQuantity;
			
			}*/
   }
   private void caculateLimitOrderTransaction(LimitOrder lo, int pos, double cv){
	   		
	//   lo.investor.updatewealth(lo.pricePerUnit, quantity, lo.type);
	 if(!lo.investor.type.equals("LPlayer")){
	   if(lo.type.equals(OrderType.PURCHASE)){
			lo.investor.position+=pos;
			lo.investor.cash-=pos*lo.pricePerUnit;
			lo.investor.wealth=lo.investor.position*lo.pricePerUnit+lo.investor.cash;
		}else{
			lo.investor.position-=pos;
			lo.investor.cash+=pos*lo.pricePerUnit;
			lo.investor.wealth=lo.investor.position*lo.pricePerUnit+lo.investor.cash;
		}
	 }
	//   System.out.println("cacuLimit!!!");
	   /*
	   int atime=(int)(myWorld.schedule.getTime());
	   double profit=0;
	   
	   if(lo.type==OrderType.SALE)
		   profit=lo.pricePerUnit-cv;// Feedback the orderProfit to investor 
	    else
	    	profit=cv-lo.pricePerUnit;
	    
	    lo.investor.updateTradingRecord(lo.ruleID,atime, profit);
	   
	   if(lo.investor.type=="IPlayer"){//知情  
			   myWorld.myMarket.countIT+=quantity;
			   myWorld.myMarket.avIR+=profit*quantity;  
			  
			  // System.out.println(lo.investor.type+lo.investor.id);
	   	}else{//GA
			
			    myWorld.myMarket.countUT+=quantity;
				
		        myWorld.myMarket.avUR+=profit*quantity;  
				  
				//System.out.println(lo.investor.type+lo.investor.id);
			}*/

   }

	   
	   // use in market;
	   public  int[] caulateSellDepth(){
		   int recordNum=41;//record 10 level orderbook and rest of the orderbook
	//for sell side
		   int[] SellDepth=new int[recordNum];
		   for(int j=0;j<recordNum;j++){
			   SellDepth[j]=0;
		   }
		
		   
		   if(!this.sellOrders.isEmpty()){
			   double tempPrice=this.sellOrders.first().pricePerUnit;
			   int j=0;
			   for(LimitOrder l: this.sellOrders){
				   if(j<(recordNum-1)){//record 1 to 10 level
				     if(tempPrice==l.pricePerUnit){
				    	 SellDepth[j]+=l.quantity;
				     }else{
				    	 j++;
				    	 tempPrice=l.pricePerUnit;
				    	 SellDepth[j]+=l.quantity;
				     }
				   }else{//record the rest order book
					   SellDepth[recordNum-1]+=l.quantity;
				   }
			   }
			   
		   }else{
			   
		   }
		   
		  
		   return SellDepth;

	   }
	   
	   public  int[] caulateBuyDepth(){
		   // for buy side
           int recordNum=41;// record 10 level and rest of the order book.
		   int[] BuyDepth=new int[recordNum];
		   for(int j=0;j<recordNum;j++){
			   BuyDepth[j]=0;
		   }
		  
		   
		   if(!this.buyOrders.isEmpty()){
			   double tempPrice=this.buyOrders.first().pricePerUnit;
			   int j=0;
			   for(LimitOrder l: this.buyOrders){
				   if(j<(recordNum-1)){//record 1 to recordNum-1 level
				     if(tempPrice==l.pricePerUnit){
				    	 BuyDepth[j]+=l.quantity;
				     }else{
				    	 j++;
				    	 tempPrice=l.pricePerUnit;
				    	 BuyDepth[j]+=l.quantity;
				     }
				   }else{//record the rest order book
					   BuyDepth[recordNum-1]+=l.quantity;
				   }
			   }
			   
		   }else{
			   
		   }
		   
		   return BuyDepth;
		   
	   }
	   
	 // cuculate order correlation
	  public void setOrderCorrelation(int currentOrderMark){
		  if(this.lastOrderMark>-1){
		  myWorld.myMarket.orderCorrellation[this.lastOrderMark][currentOrderMark]++;
		  }
		  this.lastOrderMark=currentOrderMark;
	  }
	
	
	 
	  
	   

}
