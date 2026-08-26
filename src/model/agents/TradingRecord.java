package model.agents;

public class TradingRecord {
	
	public int agentID;
	public String agentType;
	public String ruleID;
	public double profit;
	public double activeTime;
	public double expirationTime;
	
	public TradingRecord(int id, String type, double atime,String rid,double eTime){
		this.agentID=id;
		this.agentType=type;
		
		this.ruleID=rid;
		this.profit=0;
		this.activeTime=100000000;	//³ä·Ö´ó
		this.expirationTime=eTime;
	}
	public void setTradingRecord(double pro, double atime){
		this.profit=pro;
		this.activeTime=atime;//record the transaction time
	  //   System.out.println(this.agentID+this.agentType+this.ruleID+"profit:"+this.profit);
	}
	public double getActiveTime(){
		return this.activeTime;
	}

	

}
