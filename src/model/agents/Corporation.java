package model.agents;

import java.util.ArrayList;
import java.util.Vector;

import model.FinancialModel;
import model.market.books.OrderBook.OrderType;
import sim.engine.SimState;
import support.Distributions;

public class Corporation extends GenericPlayer {
	
	private Distributions randDist = null;
	private double rho = 10; //���й�˾�ķ������ˮƽ
	private double beta = 5; // ���й�˾�Ĳ���ת��ǿ��
	private double truthTelling = 0.0; // ��ʵ�������Ե�ƽ��Ч��
	private double earningsManagement = 0.0; // ӯ�������Ե�ƽ��Ч��
	private double granularity = 100;
	private double k = 300; // ӯ������
	private double g = 10; // ӯ���������
	private double upperbound = 200;
	private double lowerbound = -200;
	private double emLevel = 0; // �ۼ�ӯ�����ˮƽ
	private int lastUpdated = 0;
	private int displayTime = 10;
	
	
	private int trueTimes = 0; // ��ʵ����Ĵ���
	private int emTimes = 0; // ӯ�����Ĵ���
	private boolean failEM = false; // �ۼ�ӯ������Ƿ񳬹�ӯ�����Χ
	
	private boolean emLastTime = false;  // ��һ���Ƿ�ӯ�����
	private boolean actionLastTime = false; // ��һ���Ƿ������ѡ��
	private int tau = 2400; // һ��ķ�������
	private int startTime = 0;
	private double lastCommonvalue; // ��һ�εĻ������ֵ
	private double currentCommonvalue; // �µĻ������ֵ
	
	public Corporation() {
        
		
    }

    public void setup(int i, FinancialModel target) {
        this.myWorld = target;
        this.id = i;
        this.type = "Corporation";
        randDist = new Distributions(myWorld.random);
        this.granularity = 1.0/this.myWorld.parameterMap.get("granularity");
        this.k = this.myWorld.parameterMap.get("k");
        this.k = this.k * this.granularity;
        this.upperbound = this.k * this.g;
        this.lowerbound = -this.k * this.g;
        this.lastCommonvalue = this.myWorld.parameterMap.get("initialPrice");
        this.currentCommonvalue = this.myWorld.parameterMap.get("initialPrice");
        this.displayTime = this.myWorld.parameterMap.get("displayTime").intValue();
        this.startTime = this.myWorld.parameterMap.get("recordTime").intValue();
        this.lastUpdated = 0;
		target.schedule.scheduleRepeating(1.0, 1, this, 1.0);
    }

	@Override
	public void step(SimState state) {
		// TODO Auto-generated method stub
		int time = (int)this.myWorld.schedule.getTime();
		this.currentCommonvalue = this.myWorld.myAsset.getCommonValue();
		double realE = this.currentCommonvalue - this.lastCommonvalue;
		boolean report = (this.currentCommonvalue - this.lastCommonvalue != 0)?true:false;
		double pEM = Math.exp(this.beta * this.earningsManagement)/(Math.exp(this.beta * this.earningsManagement) + Math.exp(this.beta * this.truthTelling));
		this.myWorld.myMarket.pEM = pEM;
		this.myWorld.myMarket.pTrue = (1 - pEM);
		if (report) {
//			System.out.println(time);
			if (this.actionLastTime) {
				this.updateUtility(); // �����ϴβ��Ե�Ч��
			}
			this.actToReport(realE); // �жϱ����Ƿ���Ҫѡ�����
			this.myWorld.myMarket.newEarnings = true;
			this.lastUpdated = (int)this.myWorld.schedule.getTime();
		}else {
			if (time - this.lastUpdated > this.displayTime/2.0) {
				this.myWorld.myMarket.newEarnings = false;
			}
		}
		this.lastCommonvalue = this.currentCommonvalue; // ���»������ֵ
	}
	
	public void actToReport(double realE) {
		if (Math.abs(realE) == this.k/2) {
			this.myWorld.myMarket.updateEarnings(realE, realE, false);
			this.actionLastTime = false;
		}else {
			boolean EM = this.getStrategy();
			if (EM) { // ����ӯ��������
				
				double earnings = realE/2;
				this.emLevel += realE - earnings;
				if (this.emLevel < this.lowerbound || this.emLevel > this.upperbound) {
					this.failEM = true;//����ӯ������������ʱ��ӯ�����ʧ��
					this.myWorld.myMarket.failEM = true;
					earnings = emLevel + realE; // ϴ����
					this.myWorld.myMarket.updateEarnings(earnings, realE, false);
					this.myWorld.myMarket.earningsManagement = false;
				}
				this.myWorld.myMarket.updateEarnings(earnings, realE, true);
				this.myWorld.myMarket.earningsManagement = true;
				this.emLastTime = true;
				this.emLevel += realE - earnings;
			}else { // ������ʵ��������
				this.myWorld.myMarket.updateEarnings(realE, realE, false);
				this.myWorld.myMarket.earningsManagement = false;
				this.emLastTime = false;
			}
			this.actionLastTime = true;
		}
	}
	
	public void updateUtility() {
		
		double variance;
		double rate; // ƽ���۸��������ƽ���۸�
		double utility;
		
		int time = (int)this.myWorld.schedule.getTime();
		ArrayList<Double> pastPrices;
		pastPrices =  this.myWorld.myMarket.getPastPrices(tau);
		double avP1 = 0;
		double avP2 = 0;
		for (int i = 0; i < pastPrices.size(); i++) {
			avP1 += pastPrices.get(i);
		}
		avP1 = avP1/this.tau;
		
		if (time >= 2 * tau) {
			pastPrices =  this.myWorld.myMarket.getPastPrices(2*tau);
			for (int i = 0; i < this.tau; i++) {
				avP2 += pastPrices.get(i);
			}
			avP2 = avP2/this.tau;
		}else {
			avP2 = this.myWorld.parameterMap.get("initialPrice");
		}
		
		
		variance = this.getVariance(tau);
//		System.out.println("Last time " + this.emLastTime + ": " + variance);
//		System.out.println("Em: " + this.earningsManagement + "; Ture: " + this.truthTelling );
		rate = Math.log(avP1/avP2);
		utility = rate - 0.5*rho*variance;
		utility = -Math.exp( - this.rho * utility);
		
		// �����һ��ӯ�����ʧ���ˣ�Ч�ü���ӯ��������
		if (failEM) {
			this.earningsManagement = this.earningsManagement * this.emTimes + utility;
			this.emTimes ++;
			this.earningsManagement = this.earningsManagement/this.emTimes;
			this.failEM = false;// ����ӯ�����ʧ�ܵ�label
		}else {
			// update the lastTime utility
			if (this.emLastTime) {
				this.earningsManagement = this.earningsManagement * this.emTimes + utility;
				this.emTimes ++;
				this.earningsManagement = this.earningsManagement/this.emTimes;
			}else {
				this.truthTelling = this.truthTelling * this.trueTimes + utility;
				this.trueTimes ++;
				this.truthTelling = this.truthTelling/this.trueTimes;
			}
		}
		
	}
	
	private double getVariance(int length) {
    	double variance = 0.0;
    	double temp = 0.0;
    	double meanR = 0.0;
    	int datTau = (int)this.myWorld.parameterMap.get("T").doubleValue();
    	
    	int currentTime = (int) this.myWorld.schedule.getTime();
    	if (currentTime < length) {
    		variance = 0;
    	}else {
    		ArrayList<Double> pastPrices = new ArrayList<Double>();
        	ArrayList<Double> returns = new ArrayList<Double>();
        	pastPrices = this.myWorld.myMarket.getPastPrices(length + 1);
        	int interval = datTau/4;
        	int numObservation = length/(interval);
        	for (int i = 0; i < numObservation; i++) {
        		temp = pastPrices.get((i + 1)*interval)/pastPrices.get(i*interval);
        		temp = Math.log(temp);
        		returns.add(temp);
        		meanR += temp;
        	}
        	meanR = meanR/numObservation;
        	for (int i = 0; i < returns.size(); i++) {
        		temp = returns.get(i) - meanR;
        		variance += Math.pow(temp, 2);
        	}
//        	variance = variance/numObservation;
        	
    	}
    	
    	return variance;
    	
    }
	
	public boolean getStrategy() {
		boolean EM;		
		int time = (int)this.myWorld.schedule.getTime();
		double pEM = Math.exp(this.beta * this.earningsManagement)/(Math.exp(this.beta * this.earningsManagement) + Math.exp(this.beta * this.truthTelling));
		if (time <= this.startTime) {
			EM = Math.random() > 0.5?true:false;
		}else {
			EM = Math.random() < pEM ? true:false;
//			this.myWorld.myMarket.pEM = pEM;
//			this.myWorld.myMarket.pTrue = (1 - pEM);
//			System.out.println(pEM);
		}
		return EM;
	}

	@Override
	public void submitLimitOrder(OrderType type, int asset, double price, int quantity) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public double getWealth() {
		// TODO Auto-generated method stub
		return 0;
	}
	

}
