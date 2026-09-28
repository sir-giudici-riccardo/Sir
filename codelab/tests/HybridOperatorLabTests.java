import org.sigma.codelab.HybridOperatorLab;
import org.sigma.codelab.ScientificDomainRegistry;

public final class HybridOperatorLabTests {
    private static int passed = 0;
    private static int total = 0;

    private static void check(String name, boolean condition) {
        total++;
        if (!condition) throw new AssertionError(name);
        passed++;
    }

    private static HybridOperatorLab.HybridRequest request(
            ScientificDomainRegistry.Domain d,
            ScientificDomainRegistry.Primitive a,
            ScientificDomainRegistry.Primitive b,
            int depth, int exponent, int grade,
            HybridOperatorLab.LimitRegime limit) {
        return new HybridOperatorLab.HybridRequest(d, a, b, depth, exponent, grade, limit);
    }

    public static void main(String[] args) {
        check("depth >2 blocked", HybridOperatorLab.evaluate(request(
                ScientificDomainRegistry.Domain.Q, ScientificDomainRegistry.Primitive.INTEGER_POWER,
                ScientificDomainRegistry.Primitive.COMMUTATOR, 3, 2, 2,
                HybridOperatorLab.LimitRegime.POSITIVE_FINITE)).classification() == HybridOperatorLab.EvaluationClass.BLOCKED);

        check("depth2 requires two ops", HybridOperatorLab.evaluate(request(
                ScientificDomainRegistry.Domain.Q, ScientificDomainRegistry.Primitive.INTEGER_POWER,
                null, 2, 2, 2, HybridOperatorLab.LimitRegime.ZERO)).classification() == HybridOperatorLab.EvaluationClass.BLOCKED);

        check("N negative power requires embedding", HybridOperatorLab.evaluate(request(
                ScientificDomainRegistry.Domain.N, ScientificDomainRegistry.Primitive.INTEGER_POWER,
                null, 1, -1, 1, HybridOperatorLab.LimitRegime.NEGATIVE_FINITE)).classification() == HybridOperatorLab.EvaluationClass.REQUIRES_EMBEDDING);

        check("Z negative matrix power requires embedding", HybridOperatorLab.evaluate(request(
                ScientificDomainRegistry.Domain.Z, ScientificDomainRegistry.Primitive.MATRIX_INTEGER_POWER,
                null, 1, -1, 2, HybridOperatorLab.LimitRegime.NEGATIVE_FINITE)).classification() == HybridOperatorLab.EvaluationClass.REQUIRES_EMBEDDING);

        check("Q exact matrix pair", HybridOperatorLab.evaluate(request(
                ScientificDomainRegistry.Domain.Q, ScientificDomainRegistry.Primitive.MATRIX_INTEGER_POWER,
                ScientificDomainRegistry.Primitive.COMMUTATOR, 2, 3, 2,
                HybridOperatorLab.LimitRegime.POSITIVE_FINITE)).classification() == HybridOperatorLab.EvaluationClass.EXECUTABLE_EXACT);

        check("Qp valuation symbolic", ScientificDomainRegistry.decide(
                ScientificDomainRegistry.Domain.QP,
                ScientificDomainRegistry.Primitive.P_ADIC_VALUATION_SCALE, 1).support()
                == ScientificDomainRegistry.Support.SYMBOLIC_CONTRACT_ONLY);

        check("GF Frobenius discrete", ScientificDomainRegistry.decide(
                ScientificDomainRegistry.Domain.GF5,
                ScientificDomainRegistry.Primitive.FROBENIUS, 1).support()
                == ScientificDomainRegistry.Support.EXECUTABLE_DISCRETE);

        check("finite field limit not Archimedean", HybridOperatorLab.limitQualification(
                ScientificDomainRegistry.Domain.GF7, HybridOperatorLab.LimitRegime.ZERO_PLUS)
                .contains("not finite-field limit notions"));

        check("complex 0+ external path", HybridOperatorLab.limitQualification(
                ScientificDomainRegistry.Domain.C, HybridOperatorLab.LimitRegime.ZERO_PLUS)
                .contains("external ordered real parameter/path"));

        check("quaternion ordered product symbolic", ScientificDomainRegistry.decide(
                ScientificDomainRegistry.Domain.H,
                ScientificDomainRegistry.Primitive.ORDERED_PRODUCT, 1).support()
                == ScientificDomainRegistry.Support.SYMBOLIC_CONTRACT_ONLY);

        check("hyperreal symbolic", ScientificDomainRegistry.decide(
                ScientificDomainRegistry.Domain.HYPERREAL,
                ScientificDomainRegistry.Primitive.INTEGER_POWER, 2).support()
                == ScientificDomainRegistry.Support.SYMBOLIC_CONTRACT_ONLY);

        check("surreal symbolic", ScientificDomainRegistry.decide(
                ScientificDomainRegistry.Domain.SURREAL,
                ScientificDomainRegistry.Primitive.INTEGER_POWER, 2).support()
                == ScientificDomainRegistry.Support.SYMBOLIC_CONTRACT_ONLY);

        HybridOperatorLab.Matrix2Q a = new HybridOperatorLab.Matrix2Q(1, 1, 0, 1);
        HybridOperatorLab.Matrix2Q b = new HybridOperatorLab.Matrix2Q(1, 0, 1, 1);
        check("noncommuting fixture", !a.commutator(b).isZero());

        HybridOperatorLab.Matrix2Q d1 = new HybridOperatorLab.Matrix2Q(2, 0, 0, 3);
        HybridOperatorLab.Matrix2Q d2 = new HybridOperatorLab.Matrix2Q(5, 0, 0, 7);
        check("commuting diagonal fixture", d1.commutator(d2).isZero());
        check("power zero identity", d1.pow(0).equals(HybridOperatorLab.Matrix2Q.identity()));
        check("negative power inverse", d1.pow(-1).multiply(d1).equals(HybridOperatorLab.Matrix2Q.identity()));

        HybridOperatorLab.Matrix2Q u = new HybridOperatorLab.Matrix2Q(1, 1, 0, 1);
        HybridOperatorLab.Matrix2Q uInv = u.pow(-1);
        check("unimodular exact inverse", uInv.multiply(u).equals(HybridOperatorLab.Matrix2Q.identity()));

        HybridOperatorLab.Matrix2Q diagA = new HybridOperatorLab.Matrix2Q(1, 0, 0, 2);
        HybridOperatorLab.Matrix2Q diagB = new HybridOperatorLab.Matrix2Q(3, 0, 0, 4);
        check("resolvent commutes with commuting power", HybridOperatorLab.resolventCommutesWithPower(
                diagA, diagB,
                new HybridOperatorLab.Rational(java.math.BigInteger.ONE, java.math.BigInteger.valueOf(3)), 4));

        boolean singularBlocked = false;
        try { new HybridOperatorLab.Matrix2Q(1,2,2,4).pow(-1); }
        catch (ArithmeticException expected) { singularBlocked = true; }
        check("singular negative power fails closed", singularBlocked);

        boolean exponentBlocked = false;
        try { d1.pow(10); }
        catch (IllegalArgumentException expected) { exponentBlocked = true; }
        check("exponent outside window fails closed", exponentBlocked);

        check("plus infinity is regime", HybridOperatorLab.limitQualification(
                ScientificDomainRegistry.Domain.R, HybridOperatorLab.LimitRegime.POS_INFINITY)
                .contains("not a finite exponent"));

        check("boundary declares no language promotion", HybridOperatorLab.boundaries().contains("OPERATOR_LAB!=SIGMA_LANGUAGE_SEMANTICS"));

        System.out.println("HybridOperatorLabTests PASS " + passed + "/" + total);
    }
}
