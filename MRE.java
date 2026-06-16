import kodkod.ast.*;
import kodkod.engine.*;
import kodkod.instance.*;
import kodkod.engine.satlab.SATFactory;
import java.util.Iterator;

public class MRE {
    public static void main(String[] args) throws Exception {
        Universe univ = new Universe("N0", "N1", "N2");
        TupleFactory f = univ.factory();
        
        Relation succ = Relation.nary("succ", 2);
        Relation data = Relation.nary("node_data", 2);
        
        Bounds bounds = new Bounds(univ);
        bounds.bound(succ, f.allOf(2));
        bounds.bound(data, f.allOf(2));
        
        // Constraint: succ is a cycle of length 3
        Variable n = Variable.unary("n");
        Formula f1 = succ.function(Relation.UNIV, Relation.UNIV);
        Formula f2 = succ.transpose().function(Relation.UNIV, Relation.UNIV);
        Formula f3 = Relation.UNIV.in(n.join(succ.closure())).forAll(n.oneOf(Relation.UNIV));
        
        // Constraint: data is completely empty
        Formula f4 = data.no();
        
        Formula f_all = f1.and(f2).and(f3).and(f4);
        
        Solver solver = new Solver();
        solver.options().setSolver(SATFactory.get("sat4j"));
        solver.options().setSymmetryBreaking(20);
        
        Iterator<Solution> it = solver.solveAll(f_all, bounds);
        int count = 0;
        while(it.hasNext()) {
            Solution sol = it.next();
            if(!sol.sat()) break;
            System.out.println("Solution " + count + ": ");
            System.out.println("  succ: " + sol.instance().tuples(succ));
            System.out.println("  data: " + sol.instance().tuples(data));
            count++;
        }
        System.out.println("Total solutions: " + count);
    }
}
