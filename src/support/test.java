package support;
//import org.apache.commons.math3.analysis.UnivariateFunction;
//import org.apache.commons.math3.analysis.solvers.BrentSolver;

public class test {

//	public static void main(String[] args) {
//		int n = 10000;
//		double g = 0;
//		// TODO Auto-generated method stub
//		for (int i = 0; i < n; i++) {
//			g += generateLaplace(5);
//		}
//		g = g/n;
//		System.out.println(g);
//	}
	
	public static void main(String[] args) {
//        UnivariateFunction function = x -> Math.pow(x, 2) - 4; // Example function: f(x) = x^2 - 4
//        BrentSolver solver = new BrentSolv0, function, -10, 10);
//        System.out.println("Root: "er();
//        double root = solver.solve(10 + root);
		double p = 0;
		int times = 1000;
		for (int i = 0; i < times; i++) {
			p += 1/(1 + Math.exp(-100*(0.0 - 0.02)));
		}
		p = p/times;
		System.out.println(p);
    }
	
}
