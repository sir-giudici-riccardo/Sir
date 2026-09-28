package org.sigma.codelab;

import java.math.BigInteger;
import java.util.Objects;

/**
 * DELTA_HYBRID2_TYPED_VIEW_V1
 *
 * A bounded scientific contract and exact-rational finite-matrix demo.
 * It is NOT a new numeric field, NOT a universal operator calculus and NOT
 * promoted SIGMA language syntax.
 */
public final class HybridOperatorLab {
    private HybridOperatorLab() {}

    public static final String CONTRACT_ID = "DELTA_HYBRID2_TYPED_VIEW_V1";
    public static final int MAX_HYBRID_DEPTH = 2;
    public static final int MAX_MATRIX_GRADE = 8;
    public static final int MIN_INTEGER_EXPONENT = -9;
    public static final int MAX_INTEGER_EXPONENT = 9;

    public enum LimitRegime {
        NEG_INFINITY,
        NEGATIVE_FINITE,
        ZERO_MINUS,
        ZERO,
        ZERO_PLUS,
        POSITIVE_FINITE,
        POS_INFINITY
    }

    public enum EvaluationClass {
        EXECUTABLE_EXACT,
        EXECUTABLE_DISCRETE,
        SYMBOLIC_ONLY,
        REQUIRES_EMBEDDING,
        BLOCKED
    }

    public record HybridRequest(
            ScientificDomainRegistry.Domain domain,
            ScientificDomainRegistry.Primitive first,
            ScientificDomainRegistry.Primitive second,
            int depth,
            int exponent,
            int matrixGrade,
            LimitRegime limitRegime) {}

    public record HybridDecision(EvaluationClass classification, String reason) {}

    public static HybridDecision evaluate(HybridRequest request) {
        if (request == null || request.domain() == null || request.first() == null || request.limitRegime() == null) {
            return new HybridDecision(EvaluationClass.BLOCKED, "missing typed request field");
        }
        if (request.depth() < 1 || request.depth() > MAX_HYBRID_DEPTH) {
            return new HybridDecision(EvaluationClass.BLOCKED, "hybrid depth must be 1..2");
        }
        if (request.depth() == 1 && request.second() != null) {
            return new HybridDecision(EvaluationClass.BLOCKED, "depth=1 must not silently carry a second operator");
        }
        if (request.depth() == 2 && request.second() == null) {
            return new HybridDecision(EvaluationClass.BLOCKED, "depth=2 requires exactly two typed operators");
        }
        if (request.matrixGrade() < 1 || request.matrixGrade() > MAX_MATRIX_GRADE) {
            return new HybridDecision(EvaluationClass.BLOCKED, "finite matrix grade must be 1..8");
        }
        if (request.exponent() < MIN_INTEGER_EXPONENT || request.exponent() > MAX_INTEGER_EXPONENT) {
            return new HybridDecision(EvaluationClass.BLOCKED,
                    "integer exponent must be -9..9; ±∞ are limit regimes, not finite exponents");
        }

        ScientificDomainRegistry.Decision a = ScientificDomainRegistry.decide(request.domain(), request.first(), request.exponent());
        ScientificDomainRegistry.Decision b = request.depth() == 2
                ? ScientificDomainRegistry.decide(request.domain(), request.second(), request.exponent())
                : null;

        EvaluationClass ca = map(a.support());
        EvaluationClass cb = b == null ? ca : map(b.support());
        EvaluationClass worst = worst(ca, cb);

        String limitNote = limitQualification(request.domain(), request.limitRegime());
        String reason = "A=" + a.support() + ": " + a.reason()
                + (b == null ? "" : " | B=" + b.support() + ": " + b.reason())
                + " | " + limitNote;
        return new HybridDecision(worst, reason);
    }

    private static EvaluationClass map(ScientificDomainRegistry.Support s) {
        return switch (s) {
            case EXECUTABLE_EXACT -> EvaluationClass.EXECUTABLE_EXACT;
            case EXECUTABLE_DISCRETE -> EvaluationClass.EXECUTABLE_DISCRETE;
            case SYMBOLIC_CONTRACT_ONLY -> EvaluationClass.SYMBOLIC_ONLY;
            case REQUIRES_EXPLICIT_EMBEDDING -> EvaluationClass.REQUIRES_EMBEDDING;
            case UNSUPPORTED_FAIL_CLOSED -> EvaluationClass.BLOCKED;
        };
    }

    private static EvaluationClass worst(EvaluationClass a, EvaluationClass b) {
        if (a == EvaluationClass.BLOCKED || b == EvaluationClass.BLOCKED) return EvaluationClass.BLOCKED;
        if (a == EvaluationClass.REQUIRES_EMBEDDING || b == EvaluationClass.REQUIRES_EMBEDDING) return EvaluationClass.REQUIRES_EMBEDDING;
        if (a == EvaluationClass.SYMBOLIC_ONLY || b == EvaluationClass.SYMBOLIC_ONLY) return EvaluationClass.SYMBOLIC_ONLY;
        if (a == EvaluationClass.EXECUTABLE_DISCRETE || b == EvaluationClass.EXECUTABLE_DISCRETE) return EvaluationClass.EXECUTABLE_DISCRETE;
        return EvaluationClass.EXECUTABLE_EXACT;
    }

    public static String limitQualification(ScientificDomainRegistry.Domain domain, LimitRegime regime) {
        if (regime == LimitRegime.ZERO_MINUS || regime == LimitRegime.ZERO_PLUS) {
            if (domain == ScientificDomainRegistry.Domain.C || domain == ScientificDomainRegistry.Domain.H) {
                return "0-/0+ are directional labels for an external ordered real parameter/path; no order is imposed on " + domain.label;
            }
            if (domain == ScientificDomainRegistry.Domain.QP) {
                return "0-/0+ are external-real labels only; domain-native convergence must use the p-adic topology";
            }
            if (domain.finiteField) {
                return "0-/0+ are not finite-field limit notions; only an externally indexed family may carry those labels";
            }
            return "0-/0+ are one-sided limit labels, not additional scalar elements";
        }
        if (regime == LimitRegime.NEG_INFINITY || regime == LimitRegime.POS_INFINITY) {
            return "±∞ is a limit regime label, not a finite exponent or scalar adjoined by this lab";
        }
        return "finite/zero regime: no theorem is inferred from the label alone";
    }

    /** Exact reduced rational backed by BigInteger. */
    public static final class Rational implements Comparable<Rational> {
        public static final Rational ZERO = new Rational(BigInteger.ZERO, BigInteger.ONE);
        public static final Rational ONE = new Rational(BigInteger.ONE, BigInteger.ONE);
        private final BigInteger n;
        private final BigInteger d;

        public Rational(long value) { this(BigInteger.valueOf(value), BigInteger.ONE); }

        public Rational(BigInteger numerator, BigInteger denominator) {
            Objects.requireNonNull(numerator, "numerator");
            Objects.requireNonNull(denominator, "denominator");
            if (denominator.signum() == 0) throw new ArithmeticException("zero denominator");
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            BigInteger g = numerator.gcd(denominator);
            this.n = numerator.divide(g);
            this.d = denominator.divide(g);
        }

        public Rational add(Rational o) { return new Rational(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d)); }
        public Rational subtract(Rational o) { return new Rational(n.multiply(o.d).subtract(o.n.multiply(d)), d.multiply(o.d)); }
        public Rational multiply(Rational o) { return new Rational(n.multiply(o.n), d.multiply(o.d)); }
        public Rational divide(Rational o) {
            if (o.n.signum() == 0) throw new ArithmeticException("divide by zero");
            return new Rational(n.multiply(o.d), d.multiply(o.n));
        }
        public Rational negate() { return new Rational(n.negate(), d); }
        public boolean isZero() { return n.signum() == 0; }
        @Override public int compareTo(Rational o) { return n.multiply(o.d).compareTo(o.n.multiply(d)); }
        @Override public boolean equals(Object x) { return x instanceof Rational r && n.equals(r.n) && d.equals(r.d); }
        @Override public int hashCode() { return Objects.hash(n, d); }
        @Override public String toString() { return d.equals(BigInteger.ONE) ? n.toString() : n + "/" + d; }
    }

    /** Exact 2x2 rational matrix for finite algebraic identity checks. */
    public static final class Matrix2Q {
        public final Rational a, b, c, d;
        public Matrix2Q(long a, long b, long c, long d) {
            this(new Rational(a), new Rational(b), new Rational(c), new Rational(d));
        }
        public Matrix2Q(Rational a, Rational b, Rational c, Rational d) {
            this.a = Objects.requireNonNull(a); this.b = Objects.requireNonNull(b);
            this.c = Objects.requireNonNull(c); this.d = Objects.requireNonNull(d);
        }
        public static Matrix2Q identity() { return new Matrix2Q(1, 0, 0, 1); }
        public Matrix2Q add(Matrix2Q o) { return new Matrix2Q(a.add(o.a), b.add(o.b), c.add(o.c), d.add(o.d)); }
        public Matrix2Q subtract(Matrix2Q o) { return new Matrix2Q(a.subtract(o.a), b.subtract(o.b), c.subtract(o.c), d.subtract(o.d)); }
        public Matrix2Q multiply(Matrix2Q o) {
            return new Matrix2Q(
                    a.multiply(o.a).add(b.multiply(o.c)),
                    a.multiply(o.b).add(b.multiply(o.d)),
                    c.multiply(o.a).add(d.multiply(o.c)),
                    c.multiply(o.b).add(d.multiply(o.d)));
        }
        public Matrix2Q scale(Rational s) { return new Matrix2Q(a.multiply(s), b.multiply(s), c.multiply(s), d.multiply(s)); }
        public Rational determinant() { return a.multiply(d).subtract(b.multiply(c)); }
        public Matrix2Q inverse() {
            Rational det = determinant();
            if (det.isZero()) throw new ArithmeticException("singular matrix");
            return new Matrix2Q(d.divide(det), b.negate().divide(det), c.negate().divide(det), a.divide(det));
        }
        public Matrix2Q pow(int exponent) {
            if (exponent < MIN_INTEGER_EXPONENT || exponent > MAX_INTEGER_EXPONENT) {
                throw new IllegalArgumentException("exponent outside -9..9");
            }
            if (exponent == 0) return identity();
            Matrix2Q base = exponent < 0 ? inverse() : this;
            int k = Math.abs(exponent);
            Matrix2Q result = identity();
            while (k > 0) {
                if ((k & 1) == 1) result = result.multiply(base);
                k >>= 1;
                if (k > 0) base = base.multiply(base);
            }
            return result;
        }
        public Matrix2Q commutator(Matrix2Q o) { return multiply(o).subtract(o.multiply(this)); }
        public Matrix2Q resolvent(Rational lambda) {
            return identity().subtract(scale(lambda)).inverse();
        }
        public boolean isZero() { return a.isZero() && b.isZero() && c.isZero() && d.isZero(); }
        @Override public boolean equals(Object x) {
            return x instanceof Matrix2Q m && a.equals(m.a) && b.equals(m.b) && c.equals(m.c) && d.equals(m.d);
        }
        @Override public int hashCode() { return Objects.hash(a,b,c,d); }
        @Override public String toString() { return "[["+a+","+b+"],["+c+","+d+"]]"; }
    }

    /** Exact resolvent commutation identity check for a finite Q-matrix fixture. */
    public static boolean resolventCommutesWithPower(Matrix2Q a, Matrix2Q b, Rational lambda, int n) {
        if (n < 0 || n > MAX_INTEGER_EXPONENT) throw new IllegalArgumentException("n must be 0..9");
        Matrix2Q r = a.resolvent(lambda);
        Matrix2Q bp = b.pow(n);
        return r.multiply(bp).equals(bp.multiply(r));
    }

    public static String boundaries() {
        return "HYBRID_DEPTH<=2 / OPERATOR_LAB!=SIGMA_LANGUAGE_SEMANTICS / "
                + "FINITE_FIXTURE!=LIMIT_THEOREM / NO_NEW_FIELD_CLAIM / VALIDATION_TRANSFER_NONE";
    }
}
