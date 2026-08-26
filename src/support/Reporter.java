package support;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;

import model.FinancialModel;

import sim.engine.SimState;
import sim.engine.Steppable;

public class Reporter implements Steppable {

	private static final long serialVersionUID = 1L;

	BufferedWriter outPrices;
	
	BufferedWriter outParameters;

	FinancialModel myModel;
	private int time;
	private double recordTime;
	private double endTime;

	public Reporter(FinancialModel myModel) {

		this.myModel = myModel;
		int recordNum=40;//record 10level of order book and rest of orderbook
        int forecastNum=0;// the number of forecasting conditions
        int orderMark=4;
		try {

			String temp="";
	
			//获取系统时间并命名数据文件，by WEI
			Calendar calendar=Calendar.getInstance();
			temp=temp +calendar.get(calendar.YEAR)+"_"+calendar.get(calendar.MONTH)+"_"+calendar.get(calendar.DATE)+"_"+calendar.get(Calendar.HOUR_OF_DAY)+"_"+calendar.get(Calendar.MINUTE)+"_"+calendar.get(Calendar.SECOND);
			outPrices = new BufferedWriter(new FileWriter(temp+"timeSeries.txt", true));

			temp="";
			temp = temp + "common value" + "	";
			temp = temp + "closeAskPrice" + "	";
			temp = temp + "closeBidPrice" + "	";
			temp = temp + "tPrice" + "	";
			temp = temp + "volume" + "	";
		
			 temp=temp + "lask"+ "	";
			 temp=temp + "lbid"+ "	";
			
            temp=temp + "lda"+ "	";
            temp=temp + "ldaa"+ "	";
            temp=temp + "ldb"+ "	";
            temp=temp + "ldbb"+"	";
            
         //   temp=temp + "GF"+"	";
            
            temp=temp + "BLO"+"	";
            temp=temp + "SLO"+"	";
            temp=temp + "BMO"+"	";
            temp=temp + "SMO"+"	";
            temp=temp + "BLQ"+"	";
            temp=temp + "SLQ"+"	";
            temp=temp + "BMQ"+"	";
            temp=temp + "SMQ"+"	";
            
            temp=temp + "WF"+"	";
           
            temp=temp + "WBSV"+"	";
            temp=temp + "WN"+"	";
           
            
            for(int i=1;i<=recordNum;i++){
            	temp=temp + "da"+i+ "	";
            }
            
            for(int i=1;i<=recordNum;i++){
            	temp=temp + "db"+i+ "	";
            }
            temp=temp + "pEM"+"	";
            temp=temp + "pTrue"+"	";
            
            temp=temp + "end";
           
            
            
            
            
			outPrices.write(temp);
			outPrices.newLine();
			
		} catch (IOException e) {

			e.printStackTrace();
		}
     

	}

	
	public void step(SimState state) {

		if (myModel.schedule.getTime() == 0) {
			
			this.endTime=myModel.parameterMap.get("maxT");
			this.recordTime=myModel.parameterMap.get("recordTime");
			

		}
        
		
	  if(myModel.schedule.getTime()>=recordTime){//从61200期后开始记录
		    
		  int recordNum=40;//record 10level of order book and rest of orderbook
		  int forecastNum=0;
		  int orderMark=4;
		try {

			for (int asset =0; asset < myModel.parameterMap.get("numAssets"); asset++) {
				int[] sellDepth=new int[recordNum];
				sellDepth=myModel.myMarket.getSellDepthForAsset(0);
				int[] buyDepth=new int[recordNum];
				buyDepth=myModel.myMarket.getBuyDepthForAsset(0);
				int[] gaBitUsed=new int[forecastNum];
				gaBitUsed=myModel.myMarket.getGaBitUsed();
				
				
				int[][] orderCorrelation=new int[orderMark][orderMark];
				orderCorrelation=myModel.myMarket.getOrderCorrelation();
			String temp = "";
		
			temp = temp + myModel.myAsset.getCommonValue()+ "	";
			temp = temp + myModel.myMarket.getAskPriceForAsset(asset)+ "	";
			temp = temp + myModel.myMarket.getBidPriceForAsset(asset)+ "	";
			temp = temp + myModel.myMarket.getTPriceForAsset(asset) + "	";
			temp = temp + myModel.myMarket.getVolumeForAsset(asset) + "	";
			
			temp = temp + myModel.myMarket.askP.get(0)+ "	";
			temp = temp + myModel.myMarket.bidP.get(0)+ "	";
			
			temp=temp+myModel.myMarket.da.get(0)+ "	";
			temp=temp+myModel.myMarket.daa.get(0)+ "	";
			temp=temp+myModel.myMarket.db.get(0)+ "	";
			temp=temp+myModel.myMarket.dbb.get(0)+ "	";
			
		//	temp=temp+myModel.myMarket.gf+ "	";
			
			temp=temp+myModel.myMarket.countBLO+ "	";
			temp=temp+myModel.myMarket.countSLO+ "	";
			temp=temp+myModel.myMarket.countBMO+ "	";
			temp=temp+myModel.myMarket.countSMO+ "	";
			temp=temp+myModel.myMarket.BLOQuantity+ "	";
			temp=temp+myModel.myMarket.SLOQuantity+ "	";
			temp=temp+myModel.myMarket.BMOQuantity+ "	";
			temp=temp+myModel.myMarket.SMOQuantity+ "	";
			
			temp=temp+myModel.myMarket.wf+ "	";
		
			temp=temp+myModel.myMarket.wbsv+ "	";
			temp=temp+myModel.myMarket.wn+ "	";
		
			
		    for(int i=0;i<recordNum;i++){
		    	temp=temp+sellDepth[i]+"	";
            }
            for(int i=0;i<recordNum;i++){
            	 temp=temp+buyDepth[i]+"	";
            }
            
            temp=temp+myModel.myMarket.pEM+ "	";
            temp=temp+myModel.myMarket.pTrue+ "	";
            
            temp=temp + "0";
           
		    
		    
		//	 }
			outPrices.write(temp);
			outPrices.newLine();			

			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//reset;
		myModel.myMarket.countBLO=0;
		myModel.myMarket.countSLO=0;
		myModel.myMarket.countBMO=0;
		myModel.myMarket.countSMO=0;
		myModel.myMarket.BLOQuantity=0;
		myModel.myMarket.SLOQuantity=0;
		myModel.myMarket.BMOQuantity=0;
		myModel.myMarket.SMOQuantity=0;
	  }

	}	

	public void finishAll() {

		try {
			outPrices.flush();
			outPrices.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
