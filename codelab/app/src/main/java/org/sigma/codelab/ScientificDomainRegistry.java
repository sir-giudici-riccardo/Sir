package org.sigma.codelab;

import java.util.EnumSet;

/**
 * Conservative typed registry for the experimental Δ Hybrid Operator Lab.
 *
 * This is a routing/contract layer, not a new mathematical field hierarchy and
 * not SIGMA language semantics. Unsupported arithmetic is kept symbolic.
 */
public final class ScientificDomainRegistry {
    private ScientificDomainRegistry() {}

    public enum Domain {
        N("N", "COMMUTATIVE_SEMIRING", true, true, false, false, false),
        Z("Z", "COMMUTATIVE_RING", true, true, false, false, false),
        Q("Q", "ORDERED_FIELD", true, true, true, false, true),
        ALGEBRAIC_Q_SQRT2("Q(sqrt2)⊂Qbar", "EXECUTED_ALGEBRAIC_SUBFIELD_HARNESS", true, true, true, false, false),
        R("R", "REAL_CLOSED_ORDERED_FIELD", true, true, true, false, false),
        C("C", "ALGEBRAICALLY_CLOSED_FIELD", true, false, false, false, false),
        H("H", "NONCOMMUTATIVE_DIVISION_ALGEBRA", false, false, false, false, false),
        QP("Qp", "NON_ARCHIMEDEAN_FIELD", true, false, false, false, false),
        GF5("GF(5)", "FINITE_FIELD", true, false, false, true, true),
        GF7("GF(7)", "FINITE_FIELD", true, false, false, true, true),
        HYPERREAL("Hyperreal", "MODEL_DEPENDENT_NON_ARCHIMEDEAN_ORDERED_FIELD", true, true, false, false, false),
        SURREAL("Surreal", "PROPER_CLASS_ORDERED_REAL_CLOSED_FIELD_STRUCTURE", true, true, false, false, false);

        public final String label;
        public final String structure;
        public final boolean commutativeMultiplication;
        public final boolean globallyOrdered;
        public final boolean exactRationalMatrixDemo;
        public final boolean finiteField;
        public final boolean nativeDiscreteDemo;

        Domain(String label, String structure, boolean commutativeMultiplication,
               boolean globallyOrdered, boolean exactRationalMatrixDemo,
               boolean finiteField, boolean nativeDiscreteDemo) {
            this.label = label;
            this.structure = structure;
            this.commutativeMultiplication = commutativeMultiplication;
            this.globallyOrdered = globallyOrdered;
            this.exactRationalMatrixDemo = exactRationalMatrixDemo;
            this.finiteField = finiteField;
            this.nativeDiscreteDemo = nativeDiscreteDemo;
        }
    }

    public enum Primitive {
        INTEGER_POWER,
        MATRIX_INTEGER_POWER,
        RESOLVENT,
        COMMUTATOR,
        COMPOSITION,
        FROBENIUS,
        P_ADIC_VALUATION_SCALE,
        FORWARD_DIFFERENCE,
        ORDERED_PRODUCT,
        LIMIT_REGIME
    }

    public enum Support {
        EXECUTABLE_EXACT,
        EXECUTABLE_DISCRETE,
        SYMBOLIC_CONTRACT_ONLY,
        REQUIRES_EXPLICIT_EMBEDDING,
        UNSUPPORTED_FAIL_CLOSED
    }

    public record Decision(Support support, String reason) {}

    public static Decision decide(Domain domain, Primitive primitive, int exponent) {
        if (domain == null || primitive == null) {
            return new Decision(Support.UNSUPPORTED_FAIL_CLOSED, "domain/primitive missing");
        }
        if (exponent < -9 || exponent > 9) {
            return new Decision(Support.UNSUPPORTED_FAIL_CLOSED,
                    "finite integer exponent window is -9..9; ±∞ are limit regimes, not exponent values");
        }

        switch (primitive) {
            case FORWARD_DIFFERENCE:
                if (domain == Domain.N || domain == Domain.Z) {
                    return new Decision(Support.EXECUTABLE_DISCRETE,
                            "discrete difference/summation lane; not transferred from real derivative semantics");
                }
                return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                        "difference operator requires an explicit discrete state model");

            case FROBENIUS:
                if (domain == Domain.GF5 || domain == Domain.GF7) {
                    return new Decision(Support.EXECUTABLE_DISCRETE,
                            "finite-field Frobenius/power dynamics are domain-native");
                }
                return new Decision(Support.UNSUPPORTED_FAIL_CLOSED,
                        "Frobenius lane is reserved for finite fields in this harness");

            case P_ADIC_VALUATION_SCALE:
                if (domain == Domain.QP) {
                    return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                            "Qp requires native p-adic arithmetic/valuation implementation; binary64 is not substituted");
                }
                return new Decision(Support.UNSUPPORTED_FAIL_CLOSED,
                        "p-adic valuation scaling is not an Archimedean radial operator");

            case ORDERED_PRODUCT:
                if (domain == Domain.H) {
                    return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                            "quaternion multiplication order is semantically significant; no native H arithmetic here");
                }
                return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                        "ordered product may be represented but is only essential for noncommutative lanes");

            case LIMIT_REGIME:
                if (domain.finiteField) {
                    return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                            "one-sided/Archimedean limits are external-parameter labels, not finite-field topology");
                }
                if (domain == Domain.QP) {
                    return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                            "use p-adic topology for domain-native limits; real 0± labels are external-parameter only");
                }
                if (domain == Domain.C || domain == Domain.H) {
                    return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                            "0± requires an external ordered real parameter/path; no global order is inferred on this domain");
                }
                return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                        "limit classifier is symbolic; finite samples do not establish a limit theorem");

            case INTEGER_POWER:
                if ((domain == Domain.N || domain == Domain.Z) && exponent < 0) {
                    return new Decision(Support.REQUIRES_EXPLICIT_EMBEDDING,
                            "negative powers generally leave N/Z; no silent coercion");
                }
                if (domain == Domain.Q) {
                    return new Decision(Support.EXECUTABLE_EXACT,
                            "exact rational scalar/matrix infrastructure available for bounded integer powers");
                }
                if (domain == Domain.GF5 || domain == Domain.GF7) {
                    return new Decision(Support.EXECUTABLE_DISCRETE,
                            "integer power is finite-field native; inverse requires nonzero element");
                }
                if (domain == Domain.H) {
                    return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                            "integer powers are meaningful but native quaternion arithmetic is not implemented in this lab");
                }
                if (domain == Domain.QP || domain == Domain.HYPERREAL || domain == Domain.SURREAL
                        || domain == Domain.ALGEBRAIC_Q_SQRT2 || domain == Domain.R || domain == Domain.C) {
                    return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                            "mathematical operation is domain-dependent; current executable lab does not impersonate a missing native arithmetic");
                }
                return new Decision(Support.EXECUTABLE_DISCRETE, "bounded nonnegative integer power");

            case MATRIX_INTEGER_POWER:
            case RESOLVENT:
            case COMMUTATOR:
                if (domain == Domain.Q) {
                    return new Decision(Support.EXECUTABLE_EXACT,
                            "exact 2x2 rational matrix lane; grade and exponent are bounded");
                }
                if (domain == Domain.N || domain == Domain.Z) {
                    if (primitive == Primitive.MATRIX_INTEGER_POWER && exponent >= 0) {
                        return new Decision(Support.EXECUTABLE_DISCRETE,
                                "positive integer matrix power preserves the integral lane when arithmetic closes");
                    }
                    return new Decision(Support.REQUIRES_EXPLICIT_EMBEDDING,
                            "inverse/resolvent/negative matrix power can leave N/Z");
                }
                if (domain == Domain.H) {
                    return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                            "quaternionic matrices need an explicit left/right module and ordered multiplication convention");
                }
                return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                        "matrix operation retained as typed contract until a native arithmetic implementation is supplied");

            case COMPOSITION:
                return new Decision(Support.SYMBOLIC_CONTRACT_ONLY,
                        "composition is admitted only after both constituent operators are independently admissible");

            default:
                return new Decision(Support.UNSUPPORTED_FAIL_CLOSED, "unhandled primitive");
        }
    }

    public static EnumSet<Primitive> nativeSpecialPrimitives(Domain domain) {
        if (domain == Domain.GF5 || domain == Domain.GF7) return EnumSet.of(Primitive.FROBENIUS, Primitive.INTEGER_POWER);
        if (domain == Domain.QP) return EnumSet.of(Primitive.P_ADIC_VALUATION_SCALE, Primitive.INTEGER_POWER);
        if (domain == Domain.H) return EnumSet.of(Primitive.ORDERED_PRODUCT, Primitive.INTEGER_POWER);
        if (domain == Domain.N || domain == Domain.Z) return EnumSet.of(Primitive.FORWARD_DIFFERENCE, Primitive.INTEGER_POWER);
        return EnumSet.of(Primitive.INTEGER_POWER);
    }
}
