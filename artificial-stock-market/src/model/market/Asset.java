package model.market;

import java.math.BigDecimal;
import java.util.Vector;
import model.FinancialModel;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.distribution.Normal;
import support.Distributions;

import umontreal.iro.lecuyer.stochprocess.*;
import umontreal.iro.lecuyer.rng.MRG32k3a;
import umontreal.iro.lecuyer.rng.RandomStream;

public class Asset implements Steppable {

	public FinancialModel myWorld;
	private Distributions randDist = null;
	public Vector<Double> commonValueSeries = new Vector<Double>();
	
//	private GeometricBrownianMotion gbm=null;
	
	 //设定资产的公认价值 by wei
	public  double commonValue;//初始值应该获取初始价格
	public Vector<Double> commonValueHistory = new Vector<Double>();//by WEI
	private double granularity;//设定最小报价单位的参数
	private double mu; // drift
	private double sigma; // volatility
	private double Po; // initial price
	private int maxTau;//信息延后期
	private int k;

	private double T;
  
	private GeometricBrownianMotion gbm;
	
	
	
	public Asset(FinancialModel myWorld) {
		this.myWorld = myWorld;
		this.T=myWorld.parameterMap.get("T");
		
		
		maxTau=myWorld.parameterMap.get("tau").intValue();
	//	System.out.println("tau "+tau);
		randDist = new Distributions(myWorld.random);
		//gbm=new GeometricBrownianMotion;
		commonValue=randDist.roundDouble(myWorld.parameterMap.get("initialPrice"));
		
		granularity=myWorld.parameterMap.get("granularity");
		this.mu=myWorld.parameterMap.get("mu");
		this.sigma=myWorld.parameterMap.get("sigma");
		this.Po=myWorld.parameterMap.get("initialPrice");
		commonValueHistory.add(commonValue); //第0位已经赋值,等于0时刻的资产真实价值
		this.k= myWorld.parameterMap.get("k").intValue();
		
//		createCommonValueSeries(Po, mu, sigma);
		
		
		//System.out.println(commonValueHistory.get(0));
	
	/*	    
		for (int o = 0; o < myWorld.parameterMap.get("numAssets"); o++) {

		}
    */
	}
	public void step(SimState state) {
		// TODO Auto-generated method stub
	//	setCommonValue();
	//	int vp[]={1,5,15,30,60,240};
	//	int x=(int)(6*Math.random());
		this.setCommonValue();
		
		int time=(int)myWorld.schedule.getTime();
		
	//	time = (int)myWorld.schedule.getTime();
	//	int num =randDist.nextPoisson(1/vp[x]);
	//	for(int i=0;i<num;i++){
//		this.commonValue = commonValueSeries.elementAt(time);
		if(time>0) {
			commonValueHistory.add(commonValue);
		}
	}
	
	public void setCommonValue() {
		int time = (int)this.myWorld.schedule.getTime() + 1;
//		int tau = 4 * this.myWorld.parameterMap.get("T").intValue();
		int tau = 2400;

		if (time%tau == 0) {
			double newE = 0;
			if (myWorld.random.nextBoolean(0.5)) {
				newE = k/granularity;
			}else {
				newE = -k/granularity;
			}
			
			if (myWorld.random.nextBoolean(0.3)) {
				newE = 0.5 * newE;
			}
			if (this.commonValue <= this.myWorld.parameterMap.get("initialPrice") * 0.6) {
				newE = Math.abs(newE);
			}else if(this.commonValue >= this.myWorld.parameterMap.get("initialPrice") * 1.4) {
				newE = -Math.abs(newE);
			}
			this.commonValue += newE;
			
//			
//			if (myWorld.random.nextBoolean(0.5)) {
//				if (myWorld.random.nextBoolean(0.5)) {
//					this.commonValue += 0.5*k/granularity;//增加n个最小报价单位
//				}else {
//					this.commonValue += k/granularity;//减少n个最小报价单位
//				}
//			}else {
//				if (myWorld.random.nextBoolean(0.5)) {
//					this.commonValue -= 0.5 * k/granularity;//增加n个最小报价单位
//				}else {
//					this.commonValue -= k/granularity;//减少n个最小报价单位
//				}
//			}
			if(this.commonValue<5){//the minimum price
				this.commonValue=5;
			}
		}
	}
	
	public Vector<Double> createCommonValueSeries(double initialValue, double drifit, double volatility){
		
		
		
		commonValueSeries.add(initialValue);
		double oldValue=initialValue;
		int maxT=myWorld.parameterMap.get("maxT").intValue();
		
		gbm= new GeometricBrownianMotion(initialValue, drifit, volatility, new MRG32k3a());
		gbm.setObservationTimes(1, maxT+1);
		
		for(int i=1;i<= maxT;i++){
		//	value = (1 + b + c*n.nextDouble())*oldValue;
			double x=roundPrice(gbm.nextObservation());
			commonValueSeries.add(x);
			oldValue =x;
		}
		
		return commonValueSeries;
	}
	
	public double getCommonValue(){//by WEI
	    return this.commonValue;
	}
	
	public double getCommonValueHistory(int i){
		return commonValueHistory.get(i);
	}
	private double roundPrice(double price) {
		  if (granularity == 0.0) {
			  return price;
		  } else {
			  return ((double)((int)(price * granularity + 0.5)))/granularity;
		  }
		}



	
}