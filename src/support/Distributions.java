package support;

import ec.util.*;
import java.lang.Math;
import java.math.BigDecimal;

/**
 * Distribution.java This class generates various random variables for
 * distributions not directly supported in Java
 */
public class Distributions {

	MersenneTwisterFast rand;

	public Distributions(MersenneTwisterFast r) {
		rand = r;
	}

	// rate parameter lambda is the expected number of "events" that occur per
	// unit time
	// the return value represents the number of events that "did" happen
	// according to poisson
	public int nextPoisson(double lambda) {
		double elambda = Math.exp(-1 * lambda);
		double product = 1.0;
		int count = 0;
		int result = 0;
		while (product >= elambda) {
			product *= rand.nextDouble();
			result = count;
			count++; // keep result one behind
		}
		return result;
	}

	// Returns a value according to an exponential distribution with rate
	// parameter lambda.
	public double nextExponential(double lambda) {
		double randx = rand.nextDouble();
		return -1 * lambda * Math.log(randx);
	}
	//取小数点后两位，4舍5入
	public double roundDouble(double num) {
   	  BigDecimal   bd   =   new   BigDecimal(num);   
		  bd   =   bd.setScale(2,BigDecimal.ROUND_HALF_UP);   
		  num=bd.doubleValue();
 		  return num;
 	  
 	}
	

}
