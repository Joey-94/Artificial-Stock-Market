package model.agents;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Vector;

import sim.engine.SimState;
import model.FinancialModel;
import model.market.books.DoubleAuctionOrderBook;
import model.market.books.LimitOrder;
import model.market.books.OrderBook.OrderType;
import support.Distributions;
import umontreal.iro.lecuyer.probdist.NormalDist;
import umontreal.iro.lecuyer.randvar.LaplaceGen;
import umontreal.iro.lecuyer.randvar.NormalGen;
import umontreal.iro.lecuyer.rng.MRG32k3a;
import umontreal.iro.lecuyer.rng.RandomStream;
import umontreal.iro.lecuyer.stochprocess.GeometricBrownianMotion;
//import org.apache.commons.math3.analysis.UnivariateFunction;
//import org.apache.commons.math3.analysis.solvers.BrentSolver;
//import org.apache.commons.math3.analysis.solvers.UnivariateSolver;

public class HBPlayer extends GenericPlayer {

    private Distributions randDist = null;
    
    // Investor's account
    private int Sm;
    private double Po; // Initial price
    private double commonValue;
    private int lastUpdated;
    /* All the fields following are created in the GenericPlayer */
//  public int id;
//	public String type;
//	public int tau;
//	public int hist;
//	public int position;//current holding position
//	public double cash;// cash
//	public double wealth; // wealth, initial wearlth w=So*Po;
    
    // Heterogeneous belief and parameters
    private double alph;
    private double phi;
    private double initialAlph;
    private double lambda; // the entry rate 
    private double sigma;
    private double sigmae; // the volatility of the noise
    private double sigma1;
    private double sigma2;
    private double sigman;
    private double g1;
    private double g2;
    private double gn;
    
    // Simulation parameters
    private double granularity = 0.0;
    private int reset;
    private int baseTau;
    private int tauf;
    private double T;
    private double tickSize;
    private NormalGen ng;
    private double startT;
    private int displayTime;
    ArrayList<LimitOrder> myOrders;
    private Vector<TradingRecord> TRDlist;

    public HBPlayer() {
        myOrders = new ArrayList<>();
        TRDlist = new Vector<>();
    }

    public void setup(int i, FinancialModel target) {
        this.myWorld = target;
        this.id = i;
        this.type = "HBPlayer";
        randDist = new Distributions(myWorld.random);
        
        this.initializeAgent();
        
        // Trading record
        TradingRecord TRD = new TradingRecord(this.id, this.type, (int) (myWorld.schedule.getTime()), "0", 0);
        this.TRDlist.addElement(TRD);
        target.schedule.scheduleRepeating(1.0, 1, this, 1.0);
    }

    public void step(SimState state) {
    	
    	this.checkAnnouncement();
    	int currentTime = (int)this.myWorld.schedule.getTime();
    	if (currentTime < this.startT + 1) {
    		this.submitRandomOrder();
    	}else {
    		this.generateOrders();
    	}
        this.manageOrderExecution();
    }
    
    private void checkAnnouncement() {
    	int time = (int)this.myWorld.schedule.getTime();
    	if (time - this.lastUpdated < this.displayTime) {
    		return;
    	}
    	
    	if (this.myWorld.myMarket.newEarnings) {
    		double newEarnings = 0.0;
    		double pRight = 10 * this.phi;//this.sigmoid(this.phi);
    		
    		if (Math.random() < pRight && this.myWorld.myMarket.earningsManagement) {// 当成功识别，并且上市公司进行了盈余管理的时候，才会识别出真的盈余水平
    			newEarnings = this.myWorld.myMarket.realEarnings;
    		}else {
    			newEarnings = this.myWorld.myMarket.earnings;
    		}
    		this.commonValue += newEarnings;
    		
    		if (this.myWorld.myMarket.failEM) {
    			this.commonValue = this.myWorld.myAsset.getCommonValue();
    		}

    		this.lastUpdated = (int)this.myWorld.schedule.getTime();
    		

    	}
    }
    
    private void initializeAgent() {
    	// Investor's account
    	this.Sm = this.myWorld.parameterMap.get("initialPosition").intValue();
    	this.Po = this.myWorld.parameterMap.get("initialPrice");
    	this.commonValue = this.Po;
    	this.cash = this.Sm * this.Po;
    	this.position = this.Sm;
        this.wealth = this.cash + this.position * this.Po;
        
    	// Heterogeneous belief and parameters
        this.sigma = this.myWorld.parameterMap.get("sigma");
        this.sigmae = this.myWorld.parameterMap.get("sigmae");
    	this.sigma1 = this.myWorld.parameterMap.get("sigma1");
    	this.sigma2 = this.myWorld.parameterMap.get("sigma2");
    	this.sigman = this.myWorld.parameterMap.get("sigman");
    	this.g1 = this.generateLaplace(this.sigma1);
    	this.g2 = this.generateLaplace(this.sigma2);
    	this.gn = this.generateLaplace(this.sigman);
    	double total = this.g1 + this.g2 + this.gn;
    	this.g1 = this.g1/total;
    	this.g2 = this.g2/total;
    	this.gn = this.gn/total;
//    	System.out.println("g1: " + this.g1 + "; g2: " + this.g2 + "this.gn: " + this.gn);
    	
    	this.initialAlph = myWorld.parameterMap.get("alph");
    	this.alph = this.initialAlph * (1 + g1)/(1 + g2);
    	this.alph = this.initialAlph * (1 + g1)/(1 + g2);
    	this.tauf = myWorld.parameterMap.get("tauf").intValue();
    	this.phi = myWorld.parameterMap.get("phi");
        
    	// Simulation parameters
    	this.baseTau = myWorld.parameterMap.get("tau").intValue(); // maximum investing time horizon
        this.tau = (int) (baseTau * (1 + g1)/ (1 + g2)); // investing horizon of investor i
        this.lambda = 1.0 / this.tau; // entry rate
        this.T = myWorld.parameterMap.get("T"); // simulation periods of one day
        this.startT = 2 * this.tau + 1; // the heat up time for the model
        RandomStream stream = new MRG32k3a();
        this.ng = new NormalGen(stream, 0, sigmae); // to generate noise random variable
        this.granularity = myWorld.parameterMap.get("granularity");
        this.tickSize = 1 / granularity;
        this.reset = 0;
        this.lastUpdated = 0;
        this.displayTime = this.myWorld.parameterMap.get("displayTime").intValue();
    }
    
    private double generateLaplace(double sigmak) {
        // Generate a uniformly distributed random number in the range [-0.5, 0.5]
        double u = Math.random();
        // Apply the Laplace distribution formula
        double g = -sigmak * Math.log(1 - u);
        return g;
    }
    
    private void submitRandomOrder() {
    	int ordersPlaced = randDist.nextPoisson(lambda);
    	if(ordersPlaced < 1) {
    		return;
    	}
    	double price = this.roundPrice(this.getForecastPrice());
    	double time = this.myWorld.schedule.getTime();
    	int os = 2;
    	int asset = 0;
        int orderMark = 0;
        double expirationTime = time + tau;
        String ruleID = "0";
        OrderType orderType;
        
    	if (Math.random() > 0.5) {
    		orderType = OrderType.PURCHASE;
            orderMark = 1;
            LimitOrder newOrder = new LimitOrder(this, ruleID, orderType, asset, price, os, expirationTime);
            if (myWorld.myMarket.acceptOrder(newOrder, orderMark)) {
                myOrders.add(newOrder);
                myWorld.myMarket.countBLO++;
                myWorld.myMarket.BLOQuantity += os;
            }
    	} else {
    		orderType = OrderType.SALE;
            orderMark = 3;
            LimitOrder newOrder = new LimitOrder(this, ruleID, orderType, asset, price, os, expirationTime);
            if (myWorld.myMarket.acceptOrder(newOrder, orderMark)) {
                myOrders.add(newOrder);
                myWorld.myMarket.countSLO++;
                myWorld.myMarket.SLOQuantity += os;
            }
    	}
    }
    
    private void generateOrders() {
        int ordersPlaced = randDist.nextPoisson(lambda);
        double time = myWorld.schedule.getTime();
        double expirationTime = time + tau;
        double price;
        double variance;
        int asset = 0;
        OrderType orderType;
        
        for (int i = 0; i < ordersPlaced; i++) {
            boolean trade = true;
            this.cancelOldOrders();
            String ruleID = "0";
            double ask = myWorld.myMarket.orderBooks.get(asset).getAskPrice();
            double bid = myWorld.myMarket.orderBooks.get(asset).getBidPrice();
            double pMax = getForecastPrice();
//            double pMin = getpMin();
//            pMin = Math.max(pMin, this.tickSize);
//            variance = getVariance(this.tau);
            int os = 0;
            price = this.roundPrice(pMax);
//            price = Math.random() * (pMax - pMin) + pMin;
//            price = this.roundPrice(price);
//            int piW = (int) (Math.log(pMax/price)/(this.alph * variance * price));
            this.getWealth();
            double piW = Math.random() * this.wealth;
            os = (int) (piW/price);
            os = os - this.position;
            
            int quantity = Math.abs(os);
            int orderMark = 0;
            if (price < ask && os > 0) {
                orderType = OrderType.PURCHASE;
                orderMark = 1;
                LimitOrder newOrder = new LimitOrder(this, ruleID, orderType, asset, price, quantity, expirationTime);
                if (myWorld.myMarket.acceptOrder(newOrder, orderMark)) {
                    myOrders.add(newOrder);
                    myWorld.myMarket.countBLO++;
                    myWorld.myMarket.BLOQuantity += quantity;
                }
            } else if (ask <= price && os > 0) {
                orderType = OrderType.PURCHASE;
                orderMark = 0;
                myWorld.myMarket.acceptMarketOrder(this, ruleID, orderType, price, asset, quantity, orderMark);
                myWorld.myMarket.countBMO++;
                myWorld.myMarket.BMOQuantity += quantity;
            } else if (os < 0 && price <= bid) {
                orderType = OrderType.SALE;
                orderMark = 2;
                myWorld.myMarket.acceptMarketOrder(this, ruleID, orderType, price, asset, quantity, orderMark);
                myWorld.myMarket.countSMO++;
                myWorld.myMarket.SMOQuantity += quantity;
            } else if (os < 0 && price > bid) {
                orderType = OrderType.SALE;
                orderMark = 3;
                LimitOrder newOrder = new LimitOrder(this, ruleID, orderType, asset, price, quantity, expirationTime);
                if (myWorld.myMarket.acceptOrder(newOrder, orderMark)) {
                    myOrders.add(newOrder);
                    myWorld.myMarket.countSLO++;
                    myWorld.myMarket.SLOQuantity += quantity;
                }
            }
        }
    }

    private double getForecastPrice() {
        int assetID = 0;
        double forecastP = 0.0;
    	double forecastR = 0.0;
    	
        double pf = this.commonValue;
        double pt = myWorld.myMarket.getTPriceForAsset(assetID);
        double meanR = 0.0;
        double fundamentalR = 0.0;
        fundamentalR = Math.log(pf/pt);
        
    	double epsilon = this.ng.nextDouble();
        double time = myWorld.schedule.getTime();
        
        if (time <= startT) {
            meanR = fundamentalR;
        } else {
        	double pLong = 0;
        	double pShort = 0;
        	int length1 = (int)(this.tau/4);
        	int length2 = this.tau;
        	ArrayList<Double> pastPrices = this.myWorld.myMarket.getPastPrices(length1);
        	for (int i = 0; i < length1; i++) {
        		pShort += pastPrices.get(i);
        	}
        	pastPrices = this.myWorld.myMarket.getPastPrices(length2);
        	for (int i = 0; i < length2; i++) {
        		pLong += pastPrices.get(i);
        	}
        	pShort = pShort/length1;
        	pLong = pLong/length2;
        	
        	
//        	for (int i = 0; i < this.tau; i++) {
//        		meanR += Math.log(pastPrices.get(i + 1)/pastPrices.get(i));
//        	}
//        	meanR = meanR/this.tau;
        	meanR = pShort/pLong - 1;
        }
        forecastR = this.g1 * fundamentalR + this.g2 * meanR+ this.gn * epsilon;
//    	System.out.println("g1: " + fundamentalR + "; g2: " + meanR + "this.gn: " + this.gn);

        forecastP = pt * Math.exp(forecastR);
        return forecastP;
    }
    
//    private double getpMin() {
//    	int St = this.getPosition();
//    	double forecastPrice = this.getForecastPrice();
//    	double cash = this.getCash();
//    	double conV = this.getVariance(this.tau);
//    	
//    	
//    	UnivariateFunction function = p -> (p * (Math.log(forecastPrice/p)/(this.alph*conV*p) - St) - cash);
//    	UnivariateSolver solver = new BrentSolver();
//    	double pMin = solver.solve(100, function, 5, forecastPrice);
//    	
//    	return pMin;
//    }
//    
//    static class pMinFunction implements UnivariateFunction {
//    	
//    	private final double conV;
//    	private final double cash;
//    	private final double alpha;
//    	private final double forecastPrice;
//    	private final int St;
//    	
//    	public pMinFunction(double conV, double cash, double alpha, double forecastPrice, int St) {
//    		this.conV = conV;
//    		this.cash = cash;
//    		this.alpha = alpha;
//    		this.forecastPrice = forecastPrice;
//    		this.St = St;
//    	}
//    	
//    	public double value(double p) {
//    		double pip = Math.log(this.forecastPrice/p)/(this.alpha*this.conV*p);
//    		double LHS = p*(pip - this.St);
//    		double RHS = this.cash;
//    		return (LHS - RHS);
//    	}
//    	
//    }

    private double getVariance(int length) {
    	double variance = 0.0;
    	double temp = 0.0;
    	double meanR = 0.0;
    	int currentTime = (int) this.myWorld.schedule.getTime();
    	if (currentTime < this.startT) {
    		variance = this.sigma;
    	}else {
    		ArrayList<Double> pastPrices = new ArrayList<Double>();
        	ArrayList<Double> returns = new ArrayList<Double>();
        	pastPrices = this.myWorld.myMarket.getPastPrices(length + 1);
        	for (int i = 0; i < length; i++) {
        		temp = pastPrices.get(i + 1)/pastPrices.get(i);
        		temp = Math.log(temp);
        		returns.add(temp);
        		meanR += temp;
        	}
        	meanR = meanR/length;
        	for (int i = 0; i < returns.size(); i++) {
        		temp = returns.get(i) - meanR;
        		variance += Math.pow(temp, 2);
        	}
        	variance = variance/length;
        	
    	}
    	
    	return variance;
    	
    }

    private void cancelOldOrders() {
        if (myOrders.isEmpty()) return;
        for (int i = 0; i < myOrders.size(); i++) {
            int indexToCancel = myWorld.random.nextInt(myOrders.size());
            LimitOrder lo = myOrders.get(indexToCancel);
            if (myWorld.myMarket.cancelOrder(lo)) {
                myOrders.remove(indexToCancel);
            }
        }
    }

    private void manageOrderExecution() {
        ArrayList<LimitOrder> ordersToRemove = new ArrayList<>();
        for (LimitOrder lo : myOrders) {
            LimitOrder.LimitStatus status = lo.getStatus();
            if (status != LimitOrder.LimitStatus.PENDING) {
                ordersToRemove.add(lo);
            }
        }
        myOrders.removeAll(ordersToRemove);
    }

    public double cellPrice(double price) {
        if (granularity == 0.0) {
            return price;
        } else {
            if (price * granularity == (int) (price * granularity)) {
                return price;
            } else {
                return ((double) ((int) (price * granularity) + 1)) / granularity;
            }
        }
    }

    public double cutPrice(double price) {
        if (granularity == 0.0) {
            return price;
        } else {
            return ((double) ((int) (price * granularity))) / granularity;
        }
    }

    public void submitLimitOrder(OrderType type, int asset, double price, int quantity) {
        String ruleID = "0";
        int orderMark = 1;
        if (type.equals(OrderType.SALE)) {
            orderMark = 3;
        }
        double expirationTime = myWorld.schedule.getTime() + this.tau;
        LimitOrder newOrder = new LimitOrder(this, ruleID, type, asset, price, quantity, expirationTime);
        if (myWorld.myMarket.acceptOrder(newOrder, orderMark)) {
            myOrders.add(newOrder);
        }
    }

    public void updateWealth(double p, double pos, OrderType type) {
        double mp = myWorld.myMarket.getLastTradePriceForAsset(0);
        if (type.equals(OrderType.PURCHASE)) {
            this.position += pos;
            this.cash -= pos * p;
            this.wealth = this.position * mp + this.cash;
        } else {
            this.position -= pos;
            this.cash += pos * p;
            this.wealth = this.position * mp + this.cash;
        }
    }

    public double getWealth() {
        double price = myWorld.myMarket.getLastTradePriceForAsset(0);
        this.wealth = this.cash + this.position * price;
        return this.wealth;
    }

    public int getPosition() {
        return this.position;
    }

    public double getCash() {
        return this.cash;
    }

    private void checkResetWealth() {
        double p = myWorld.myMarket.getLastTradePriceForAsset(0);
        double w = this.getWealth();
        if (w < p) {
            resetWealth();
            this.reset++;
        }
    }

    private void resetWealth() {
        this.position = Sm;
        this.cash = this.position * Po;
        this.wealth = this.cash + Po * this.position;
    }

    private double roundPrice(double price) {
        if (granularity == 0.0) {
            return price;
        } else {
            return ((double) ((int) (price * granularity + 0.5))) / granularity;
        }
    }
    
    private double sigmoid(double phi) {
    	double p = 1/(1 + Math.exp(-40*(phi - 0.02)));
    	return p;
    }
}
