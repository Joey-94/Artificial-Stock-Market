package model.agents;

import model.FinancialModel;
import model.market.books.OrderBook.OrderType;
import sim.engine.Steppable;

public abstract class GenericPlayer implements Steppable {
	
	// trader's id
	public int id;
	public String type;
	public int tau;
	public int hist;
	public int position;//current holding position
	public double cash;// cash
	public double wealth; // wealth, initial wearlth w=So*Po;
	//public double orderProfit;
	// instantiate ContModel class in order to access its variables
	public FinancialModel myWorld;

	public void setup(int i, FinancialModel target) {
		
		this.myWorld = target;
		this.id = i;
		target.schedule.scheduleRepeating(this, 1, 1.0);
		
	}
	
	public abstract void submitLimitOrder(OrderType type, int asset, double price, int quantity);
	public abstract double getWealth();
	

}
