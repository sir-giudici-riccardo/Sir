package org.sigma.codelab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class ScriptEngine {
    private ScriptEngine() {}

    public static final class Limits {
        public final long maxSteps;
        public final int maxOutputChars;
        public final long maxLoopIterations;
        public final long deadlineMs;
        public final int maxCallDepth;

        public Limits(long maxSteps, int maxOutputChars, long maxLoopIterations, long deadlineMs) {
            this(maxSteps, maxOutputChars, maxLoopIterations, deadlineMs, 64);
        }

        public Limits(long maxSteps, int maxOutputChars, long maxLoopIterations, long deadlineMs, int maxCallDepth) {
            if (maxSteps < 1 || maxOutputChars < 1 || maxLoopIterations < 1 || deadlineMs < 1 || maxCallDepth < 1) {
                throw new IllegalArgumentException("limits must be positive");
            }
            this.maxSteps = maxSteps;
            this.maxOutputChars = maxOutputChars;
            this.maxLoopIterations = maxLoopIterations;
            this.deadlineMs = deadlineMs;
            this.maxCallDepth = maxCallDepth;
        }

        public static Limits defaults() {
            return new Limits(200_000L, 65_536, 100_000L, 3_000L, 64);
        }
    }

    public static final class Result {
        public final String output;
        public final long steps;
        public final long elapsedNs;
        public final Map<String, Object> variables;

        Result(String output, long steps, long elapsedNs, Map<String, Object> variables) {
            this.output = output;
            this.steps = steps;
            this.elapsedNs = elapsedNs;
            this.variables = variables;
        }
    }

    public static final class ScriptException extends Exception {
        public ScriptException(String message) { super(message); }
    }

    public static Result execute(String source) throws ScriptException {
        return execute(source, Limits.defaults(), () -> false);
    }

    public static Result execute(String source, Limits limits, BooleanSupplier cancelled) throws ScriptException {
        if (source == null) throw new ScriptException("source is null");
        if (limits == null) limits = Limits.defaults();
        if (cancelled == null) cancelled = () -> false;

        long started = System.nanoTime();
        Parser parser = new Parser(new Lexer(source).scanTokens());
        List<Stmt> program = parser.parse();
        Context ctx = new Context(limits, cancelled, started);
        ctx.vars.put("pi", Math.PI);
        ctx.vars.put("e", Math.E);

        try {
            for (Stmt stmt : program) stmt.exec(ctx);
        } catch (ReturnSignal signal) {
            throw new ScriptException("return outside function");
        }
        return new Result(
                ctx.output.toString(),
                ctx.steps,
                System.nanoTime() - started,
                new LinkedHashMap<>(ctx.vars));
    }

    private enum TokenType {
        NUMBER, STRING, IDENT,
        LET, PRINT, IF, ELSE, WHILE, REPEAT, TRUE, FALSE, AND, OR, NOT,
        FN, RETURN,
        PLUS, MINUS, STAR, SLASH, PERCENT,
        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET, COMMA, SEMICOLON,
        EQUAL, EQUAL_EQUAL, BANG_EQUAL, LT, LTE, GT, GTE,
        EOF
    }

    private static final class Token {
        final TokenType type;
        final String lexeme;
        final Object literal;
        final int line;

        Token(TokenType type, String lexeme, Object literal, int line) {
            this.type = type;
            this.lexeme = lexeme;
            this.literal = literal;
            this.line = line;
        }
    }

    private static final class Lexer {
        private final String source;
        private final List<Token> tokens = new ArrayList<>();
        private int start;
        private int current;
        private int line = 1;

        Lexer(String source) { this.source = source; }

        List<Token> scanTokens() throws ScriptException {
            while (!isAtEnd()) {
                start = current;
                scanToken();
            }
            tokens.add(new Token(TokenType.EOF, "", null, line));
            return tokens;
        }

        private void scanToken() throws ScriptException {
            char c = advance();
            switch (c) {
                case ' ': case '\r': case '\t': break;
                case '\n': line++; add(TokenType.SEMICOLON); break;
                case ';': add(TokenType.SEMICOLON); break;
                case '+': add(TokenType.PLUS); break;
                case '-': add(TokenType.MINUS); break;
                case '*': add(TokenType.STAR); break;
                case '%': add(TokenType.PERCENT); break;
                case '(': add(TokenType.LPAREN); break;
                case ')': add(TokenType.RPAREN); break;
                case '{': add(TokenType.LBRACE); break;
                case '}': add(TokenType.RBRACE); break;
                case '[': add(TokenType.LBRACKET); break;
                case ']': add(TokenType.RBRACKET); break;
                case ',': add(TokenType.COMMA); break;
                case '=': add(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL); break;
                case '!':
                    if (match('=')) add(TokenType.BANG_EQUAL);
                    else throw error("unexpected '!'; use 'not' for logical negation");
                    break;
                case '<': add(match('=') ? TokenType.LTE : TokenType.LT); break;
                case '>': add(match('=') ? TokenType.GTE : TokenType.GT); break;
                case '/':
                    if (match('/')) skipComment();
                    else add(TokenType.SLASH);
                    break;
                case '#': skipComment(); break;
                case '"': string(); break;
                default:
                    if (isDigit(c)) number();
                    else if (isIdentStart(c)) identifier();
                    else throw error("unexpected character '" + c + "'");
            }
        }

        private void skipComment() {
            while (!isAtEnd() && peek() != '\n') advance();
        }

        private void string() throws ScriptException {
            StringBuilder value = new StringBuilder();
            while (!isAtEnd()) {
                char c = advance();
                if (c == '"') {
                    add(TokenType.STRING, value.toString());
                    return;
                }
                if (c == '\n') {
                    line++;
                    value.append('\n');
                } else if (c == '\\') {
                    if (isAtEnd()) throw error("unterminated escape sequence");
                    char e = advance();
                    switch (e) {
                        case 'n': value.append('\n'); break;
                        case 't': value.append('\t'); break;
                        case 'r': value.append('\r'); break;
                        case '"': value.append('"'); break;
                        case '\\': value.append('\\'); break;
                        default: throw error("unsupported escape \\" + e);
                    }
                } else {
                    value.append(c);
                }
            }
            throw error("unterminated string");
        }

        private void number() throws ScriptException {
            while (isDigit(peek())) advance();
            if (peek() == '.' && isDigit(peekNext())) {
                advance();
                while (isDigit(peek())) advance();
            }
            String text = source.substring(start, current);
            try {
                add(TokenType.NUMBER, Double.parseDouble(text));
            } catch (NumberFormatException e) {
                throw error("invalid number '" + text + "'");
            }
        }

        private void identifier() {
            while (isIdentPart(peek())) advance();
            String text = source.substring(start, current);
            TokenType type;
            switch (text) {
                case "let": type = TokenType.LET; break;
                case "print": type = TokenType.PRINT; break;
                case "if": type = TokenType.IF; break;
                case "else": type = TokenType.ELSE; break;
                case "while": type = TokenType.WHILE; break;
                case "repeat": type = TokenType.REPEAT; break;
                case "true": type = TokenType.TRUE; break;
                case "false": type = TokenType.FALSE; break;
                case "and": type = TokenType.AND; break;
                case "or": type = TokenType.OR; break;
                case "not": type = TokenType.NOT; break;
                case "fn": type = TokenType.FN; break;
                case "return": type = TokenType.RETURN; break;
                default: type = TokenType.IDENT;
            }
            add(type);
        }

        private boolean isAtEnd() { return current >= source.length(); }
        private char advance() { return source.charAt(current++); }
        private char peek() { return isAtEnd() ? '\0' : source.charAt(current); }
        private char peekNext() { return current + 1 >= source.length() ? '\0' : source.charAt(current + 1); }
        private boolean match(char expected) {
            if (isAtEnd() || source.charAt(current) != expected) return false;
            current++;
            return true;
        }
        private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }
        private static boolean isIdentStart(char c) { return Character.isLetter(c) || c == '_'; }
        private static boolean isIdentPart(char c) { return Character.isLetterOrDigit(c) || c == '_'; }
        private void add(TokenType type) { add(type, null); }
        private void add(TokenType type, Object literal) {
            tokens.add(new Token(type, source.substring(start, current), literal, line));
        }
        private ScriptException error(String message) {
            return new ScriptException("line " + line + ": " + message);
        }
    }

    private interface Expr { Object eval(Context ctx) throws ScriptException; }
    private interface Stmt { void exec(Context ctx) throws ScriptException; }

    private static final class UserFunction {
        final List<String> params;
        final List<Stmt> body;
        UserFunction(List<String> params, List<Stmt> body) {
            this.params = params;
            this.body = body;
        }
    }

    private static final class ReturnSignal extends RuntimeException {
        final Object value;
        ReturnSignal(Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    private static final class Context {
        final Limits limits;
        final BooleanSupplier cancelled;
        final long deadlineNs;
        final Map<String, Object> vars = new LinkedHashMap<>();
        final Map<String, UserFunction> functions = new LinkedHashMap<>();
        final StringBuilder output = new StringBuilder();
        long steps;
        int callDepth;

        Context(Limits limits, BooleanSupplier cancelled, long startedNs) {
            this.limits = limits;
            this.cancelled = cancelled;
            long budgetNs;
            try {
                budgetNs = Math.multiplyExact(limits.deadlineMs, 1_000_000L);
            } catch (ArithmeticException e) {
                budgetNs = Long.MAX_VALUE;
            }
            long deadline;
            try {
                deadline = Math.addExact(startedNs, budgetNs);
            } catch (ArithmeticException e) {
                deadline = Long.MAX_VALUE;
            }
            this.deadlineNs = deadline;
        }

        void tick() throws ScriptException {
            steps++;
            if (steps > limits.maxSteps) throw new ScriptException("execution limit: max steps exceeded");
            if (cancelled.getAsBoolean()) throw new ScriptException("execution cancelled");
            if (System.nanoTime() > deadlineNs) throw new ScriptException("execution limit: deadline exceeded");
        }

        void print(Object value) throws ScriptException {
            String text = stringify(value) + "\n";
            if (output.length() + text.length() > limits.maxOutputChars) {
                throw new ScriptException("execution limit: output too large");
            }
            output.append(text);
        }
    }

    private static final class LiteralExpr implements Expr {
        final Object value;
        LiteralExpr(Object value) { this.value = value; }
        @Override public Object eval(Context ctx) throws ScriptException { ctx.tick(); return value; }
    }

    private static final class ListExpr implements Expr {
        final List<Expr> values;
        ListExpr(List<Expr> values) { this.values = values; }
        @Override public Object eval(Context ctx) throws ScriptException {
            ctx.tick();
            List<Object> out = new ArrayList<>();
            for (Expr expr : values) out.add(expr.eval(ctx));
            return out;
        }
    }

    private static final class VariableExpr implements Expr {
        final String name;
        VariableExpr(String name) { this.name = name; }
        @Override public Object eval(Context ctx) throws ScriptException {
            ctx.tick();
            if (!ctx.vars.containsKey(name)) throw new ScriptException("undefined variable '" + name + "'");
            return ctx.vars.get(name);
        }
    }

    private static final class UnaryExpr implements Expr {
        final TokenType op;
        final Expr right;
        UnaryExpr(TokenType op, Expr right) { this.op = op; this.right = right; }
        @Override public Object eval(Context ctx) throws ScriptException {
            ctx.tick();
            Object r = right.eval(ctx);
            switch (op) {
                case MINUS: return -number(r, "unary -");
                case PLUS: return number(r, "unary +");
                case NOT: return !truthy(r);
                default: throw new ScriptException("internal: unsupported unary operator");
            }
        }
    }

    private static final class BinaryExpr implements Expr {
        final Expr left;
        final TokenType op;
        final Expr right;
        BinaryExpr(Expr left, TokenType op, Expr right) {
            this.left = left; this.op = op; this.right = right;
        }
        @Override public Object eval(Context ctx) throws ScriptException {
            ctx.tick();
            if (op == TokenType.AND) {
                Object l = left.eval(ctx);
                return truthy(l) ? truthy(right.eval(ctx)) : false;
            }
            if (op == TokenType.OR) {
                Object l = left.eval(ctx);
                return truthy(l) ? true : truthy(right.eval(ctx));
            }

            Object l = left.eval(ctx);
            Object r = right.eval(ctx);
            switch (op) {
                case PLUS:
                    if (l instanceof String || r instanceof String) return stringify(l) + stringify(r);
                    return number(l, "+") + number(r, "+");
                case MINUS: return number(l, "-") - number(r, "-");
                case STAR: return number(l, "*") * number(r, "*");
                case SLASH:
                    double d = number(r, "/");
                    if (d == 0.0) throw new ScriptException("division by zero");
                    return number(l, "/") / d;
                case PERCENT:
                    double m = number(r, "%");
                    if (m == 0.0) throw new ScriptException("modulo by zero");
                    return number(l, "%") % m;
                case EQUAL_EQUAL: return equalValues(l, r);
                case BANG_EQUAL: return !equalValues(l, r);
                case LT: return compare(l, r, "<") < 0;
                case LTE: return compare(l, r, "<=") <= 0;
                case GT: return compare(l, r, ">") > 0;
                case GTE: return compare(l, r, ">=") >= 0;
                default: throw new ScriptException("internal: unsupported binary operator");
            }
        }
    }

    private static final class CallExpr implements Expr {
        final String name;
        final List<Expr> args;
        CallExpr(String name, List<Expr> args) { this.name = name; this.args = args; }

        @Override public Object eval(Context ctx) throws ScriptException {
            ctx.tick();
            List<Object> values = new ArrayList<>();
            for (Expr arg : args) values.add(arg.eval(ctx));

            UserFunction function = ctx.functions.get(name);
            if (function != null) return callUserFunction(ctx, name, function, values);
            return callBuiltin(name, values, ctx);
        }
    }

    private static final class LetStmt implements Stmt {
        final String name; final Expr expr;
        LetStmt(String name, Expr expr) { this.name = name; this.expr = expr; }
        @Override public void exec(Context ctx) throws ScriptException {
            ctx.tick();
            ctx.vars.put(name, expr.eval(ctx));
        }
    }

    private static final class AssignStmt implements Stmt {
        final String name; final Expr expr;
        AssignStmt(String name, Expr expr) { this.name = name; this.expr = expr; }
        @Override public void exec(Context ctx) throws ScriptException {
            ctx.tick();
            if (!ctx.vars.containsKey(name)) throw new ScriptException("cannot assign undefined variable '" + name + "'");
            ctx.vars.put(name, expr.eval(ctx));
        }
    }

    private static final class PrintStmt implements Stmt {
        final Expr expr;
        PrintStmt(Expr expr) { this.expr = expr; }
        @Override public void exec(Context ctx) throws ScriptException {
            ctx.tick();
            ctx.print(expr.eval(ctx));
        }
    }

    private static final class ExprStmt implements Stmt {
        final Expr expr;
        ExprStmt(Expr expr) { this.expr = expr; }
        @Override public void exec(Context ctx) throws ScriptException { ctx.tick(); expr.eval(ctx); }
    }

    private static final class IfStmt implements Stmt {
        final Expr condition;
        final List<Stmt> thenBranch;
        final List<Stmt> elseBranch;
        IfStmt(Expr condition, List<Stmt> thenBranch, List<Stmt> elseBranch) {
            this.condition = condition; this.thenBranch = thenBranch; this.elseBranch = elseBranch;
        }
        @Override public void exec(Context ctx) throws ScriptException {
            ctx.tick();
            List<Stmt> branch = truthy(condition.eval(ctx)) ? thenBranch : elseBranch;
            if (branch != null) for (Stmt stmt : branch) stmt.exec(ctx);
        }
    }

    private static final class RepeatStmt implements Stmt {
        final Expr count;
        final List<Stmt> body;
        RepeatStmt(Expr count, List<Stmt> body) { this.count = count; this.body = body; }
        @Override public void exec(Context ctx) throws ScriptException {
            ctx.tick();
            double raw = number(count.eval(ctx), "repeat");
            if (!Double.isFinite(raw) || raw < 0 || Math.floor(raw) != raw) {
                throw new ScriptException("repeat count must be a non-negative integer");
            }
            long n = (long) raw;
            if (n > ctx.limits.maxLoopIterations) throw new ScriptException("execution limit: repeat count too large");
            for (long i = 0; i < n; i++) {
                ctx.tick();
                for (Stmt stmt : body) stmt.exec(ctx);
            }
        }
    }

    private static final class WhileStmt implements Stmt {
        final Expr condition;
        final List<Stmt> body;
        WhileStmt(Expr condition, List<Stmt> body) { this.condition = condition; this.body = body; }
        @Override public void exec(Context ctx) throws ScriptException {
            ctx.tick();
            long loops = 0;
            while (truthy(condition.eval(ctx))) {
                if (++loops > ctx.limits.maxLoopIterations) {
                    throw new ScriptException("execution limit: while loop too long");
                }
                ctx.tick();
                for (Stmt stmt : body) stmt.exec(ctx);
            }
        }
    }

    private static final class FunctionStmt implements Stmt {
        final String name;
        final List<String> params;
        final List<Stmt> body;
        FunctionStmt(String name, List<String> params, List<Stmt> body) {
            this.name = name; this.params = params; this.body = body;
        }
        @Override public void exec(Context ctx) throws ScriptException {
            ctx.tick();
            if (isBuiltinName(name)) throw new ScriptException("cannot redefine builtin function '" + name + "'");
            ctx.functions.put(name, new UserFunction(params, body));
        }
    }

    private static final class ReturnStmt implements Stmt {
        final Expr expr;
        ReturnStmt(Expr expr) { this.expr = expr; }
        @Override public void exec(Context ctx) throws ScriptException {
            ctx.tick();
            if (ctx.callDepth <= 0) throw new ScriptException("return outside function");
            Object value = expr == null ? null : expr.eval(ctx);
            throw new ReturnSignal(value);
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int current;

        Parser(List<Token> tokens) { this.tokens = tokens; }

        List<Stmt> parse() throws ScriptException {
            List<Stmt> out = new ArrayList<>();
            skipSeparators();
            while (!isAtEnd()) {
                out.add(statement());
                skipSeparators();
            }
            return out;
        }

        private Stmt statement() throws ScriptException {
            if (match(TokenType.LET)) return letStatement();
            if (match(TokenType.PRINT)) return new PrintStmt(expression());
            if (match(TokenType.IF)) return ifStatement();
            if (match(TokenType.REPEAT)) return repeatStatement();
            if (match(TokenType.WHILE)) return whileStatement();
            if (match(TokenType.FN)) return functionStatement();
            if (match(TokenType.RETURN)) return returnStatement();

            if (check(TokenType.IDENT) && checkNext(TokenType.EQUAL)) {
                String name = advance().lexeme;
                advance();
                return new AssignStmt(name, expression());
            }
            return new ExprStmt(expression());
        }

        private Stmt letStatement() throws ScriptException {
            Token name = consume(TokenType.IDENT, "expected variable name after 'let'");
            consume(TokenType.EQUAL, "expected '=' after variable name");
            return new LetStmt(name.lexeme, expression());
        }

        private Stmt ifStatement() throws ScriptException {
            Expr condition = expression();
            List<Stmt> thenBranch = block();
            skipSeparators();
            List<Stmt> elseBranch = null;
            if (match(TokenType.ELSE)) elseBranch = block();
            return new IfStmt(condition, thenBranch, elseBranch);
        }

        private Stmt repeatStatement() throws ScriptException {
            Expr count = expression();
            return new RepeatStmt(count, block());
        }

        private Stmt whileStatement() throws ScriptException {
            Expr condition = expression();
            return new WhileStmt(condition, block());
        }

        private Stmt functionStatement() throws ScriptException {
            Token name = consume(TokenType.IDENT, "expected function name after 'fn'");
            consume(TokenType.LPAREN, "expected '(' after function name");
            List<String> params = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                do {
                    if (params.size() >= 16) throw error(peek(), "too many function parameters");
                    String param = consume(TokenType.IDENT, "expected parameter name").lexeme;
                    if (params.contains(param)) throw error(previous(), "duplicate parameter '" + param + "'");
                    params.add(param);
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "expected ')' after parameters");
            return new FunctionStmt(name.lexeme, params, block());
        }

        private Stmt returnStatement() throws ScriptException {
            if (check(TokenType.SEMICOLON) || check(TokenType.RBRACE) || check(TokenType.EOF)) {
                return new ReturnStmt(null);
            }
            return new ReturnStmt(expression());
        }

        private List<Stmt> block() throws ScriptException {
            consume(TokenType.LBRACE, "expected '{'");
            List<Stmt> body = new ArrayList<>();
            skipSeparators();
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                body.add(statement());
                skipSeparators();
            }
            consume(TokenType.RBRACE, "expected '}'");
            return body;
        }

        private Expr expression() throws ScriptException { return or(); }

        private Expr or() throws ScriptException {
            Expr expr = and();
            while (match(TokenType.OR)) expr = new BinaryExpr(expr, previous().type, and());
            return expr;
        }

        private Expr and() throws ScriptException {
            Expr expr = equality();
            while (match(TokenType.AND)) expr = new BinaryExpr(expr, previous().type, equality());
            return expr;
        }

        private Expr equality() throws ScriptException {
            Expr expr = comparison();
            while (match(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL)) {
                TokenType op = previous().type;
                expr = new BinaryExpr(expr, op, comparison());
            }
            return expr;
        }

        private Expr comparison() throws ScriptException {
            Expr expr = term();
            while (match(TokenType.LT, TokenType.LTE, TokenType.GT, TokenType.GTE)) {
                TokenType op = previous().type;
                expr = new BinaryExpr(expr, op, term());
            }
            return expr;
        }

        private Expr term() throws ScriptException {
            Expr expr = factor();
            while (match(TokenType.PLUS, TokenType.MINUS)) {
                TokenType op = previous().type;
                expr = new BinaryExpr(expr, op, factor());
            }
            return expr;
        }

        private Expr factor() throws ScriptException {
            Expr expr = unary();
            while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
                TokenType op = previous().type;
                expr = new BinaryExpr(expr, op, unary());
            }
            return expr;
        }

        private Expr unary() throws ScriptException {
            if (match(TokenType.MINUS, TokenType.PLUS, TokenType.NOT)) {
                return new UnaryExpr(previous().type, unary());
            }
            return primary();
        }

        private Expr primary() throws ScriptException {
            if (match(TokenType.NUMBER, TokenType.STRING)) return new LiteralExpr(previous().literal);
            if (match(TokenType.TRUE)) return new LiteralExpr(true);
            if (match(TokenType.FALSE)) return new LiteralExpr(false);

            if (match(TokenType.LBRACKET)) {
                List<Expr> values = new ArrayList<>();
                if (!check(TokenType.RBRACKET)) {
                    do {
                        if (values.size() >= 10_000) throw error(peek(), "list literal too large");
                        values.add(expression());
                    } while (match(TokenType.COMMA));
                }
                consume(TokenType.RBRACKET, "expected ']' after list values");
                return new ListExpr(values);
            }

            if (match(TokenType.IDENT)) {
                String name = previous().lexeme;
                if (match(TokenType.LPAREN)) {
                    List<Expr> args = new ArrayList<>();
                    if (!check(TokenType.RPAREN)) {
                        do {
                            if (args.size() >= 16) throw error(peek(), "too many function arguments");
                            args.add(expression());
                        } while (match(TokenType.COMMA));
                    }
                    consume(TokenType.RPAREN, "expected ')' after function arguments");
                    return new CallExpr(name, args);
                }
                return new VariableExpr(name);
            }

            if (match(TokenType.LPAREN)) {
                Expr expr = expression();
                consume(TokenType.RPAREN, "expected ')'");
                return expr;
            }
            throw error(peek(), "expected expression");
        }

        private void skipSeparators() {
            while (match(TokenType.SEMICOLON)) {}
        }
        private boolean match(TokenType... types) {
            for (TokenType type : types) {
                if (check(type)) { advance(); return true; }
            }
            return false;
        }
        private Token consume(TokenType type, String message) throws ScriptException {
            if (check(type)) return advance();
            throw error(peek(), message);
        }
        private boolean check(TokenType type) { return peek().type == type; }
        private boolean checkNext(TokenType type) {
            return current + 1 < tokens.size() && tokens.get(current + 1).type == type;
        }
        private Token advance() { if (!isAtEnd()) current++; return previous(); }
        private boolean isAtEnd() { return peek().type == TokenType.EOF; }
        private Token peek() { return tokens.get(current); }
        private Token previous() { return tokens.get(current - 1); }
        private ScriptException error(Token token, String message) {
            return new ScriptException("line " + token.line + ": " + message);
        }
    }

    private static Object callUserFunction(Context ctx, String name, UserFunction function, List<Object> args)
            throws ScriptException {
        if (args.size() != function.params.size()) {
            throw new ScriptException(name + ": expected " + function.params.size() + " argument(s), got " + args.size());
        }
        if (ctx.callDepth >= ctx.limits.maxCallDepth) {
            throw new ScriptException("execution limit: maximum function call depth exceeded");
        }

        Map<String, Object> saved = new LinkedHashMap<>(ctx.vars);
        ctx.callDepth++;
        try {
            for (int i = 0; i < function.params.size(); i++) {
                ctx.vars.put(function.params.get(i), args.get(i));
            }
            for (Stmt stmt : function.body) stmt.exec(ctx);
            return null;
        } catch (ReturnSignal signal) {
            return signal.value;
        } finally {
            ctx.callDepth--;
            ctx.vars.clear();
            ctx.vars.putAll(saved);
        }
    }

    private static Object callBuiltin(String name, List<Object> args, Context ctx) throws ScriptException {
        switch (name) {
            case "sqrt": requireArgs(name, args, 1); return Math.sqrt(number(args.get(0), name));
            case "abs": requireArgs(name, args, 1); return Math.abs(number(args.get(0), name));
            case "sin": requireArgs(name, args, 1); return Math.sin(number(args.get(0), name));
            case "cos": requireArgs(name, args, 1); return Math.cos(number(args.get(0), name));
            case "tan": requireArgs(name, args, 1); return Math.tan(number(args.get(0), name));
            case "log": requireArgs(name, args, 1); return Math.log(number(args.get(0), name));
            case "exp": requireArgs(name, args, 1); return Math.exp(number(args.get(0), name));
            case "floor": requireArgs(name, args, 1); return Math.floor(number(args.get(0), name));
            case "ceil": requireArgs(name, args, 1); return Math.ceil(number(args.get(0), name));
            case "round": requireArgs(name, args, 1); return (double) Math.round(number(args.get(0), name));
            case "pow":
                requireArgs(name, args, 2);
                return Math.pow(number(args.get(0), name), number(args.get(1), name));
            case "min":
                requireArgs(name, args, 2);
                return Math.min(number(args.get(0), name), number(args.get(1), name));
            case "max":
                requireArgs(name, args, 2);
                return Math.max(number(args.get(0), name), number(args.get(1), name));
            case "clamp":
                requireArgs(name, args, 3);
                double x = number(args.get(0), name);
                double lo = number(args.get(1), name);
                double hi = number(args.get(2), name);
                if (lo > hi) throw new ScriptException("clamp: minimum exceeds maximum");
                return Math.max(lo, Math.min(hi, x));
            case "len":
                requireArgs(name, args, 1);
                if (args.get(0) instanceof List) return (double) ((List<?>) args.get(0)).size();
                return (double) stringify(args.get(0)).length();
            case "str":
                requireArgs(name, args, 1);
                return stringify(args.get(0));
            case "num":
                requireArgs(name, args, 1);
                Object v = args.get(0);
                if (v instanceof Number) return ((Number) v).doubleValue();
                try {
                    return Double.parseDouble(stringify(v));
                } catch (NumberFormatException e) {
                    throw new ScriptException("num: value is not numeric");
                }
            case "type":
                requireArgs(name, args, 1);
                return typeName(args.get(0));
            case "get": {
                requireArgs(name, args, 2);
                List<Object> list = list(args.get(0), name);
                int index = index(args.get(1), list.size(), name);
                return list.get(index);
            }
            case "set": {
                requireArgs(name, args, 3);
                List<Object> list = list(args.get(0), name);
                int index = index(args.get(1), list.size(), name);
                return list.set(index, args.get(2));
            }
            case "push": {
                requireArgs(name, args, 2);
                List<Object> list = list(args.get(0), name);
                if (list.size() >= 100_000) throw new ScriptException("push: list size limit exceeded");
                list.add(args.get(1));
                return (double) list.size();
            }
            case "pop": {
                requireArgs(name, args, 1);
                List<Object> list = list(args.get(0), name);
                if (list.isEmpty()) throw new ScriptException("pop: empty list");
                return list.remove(list.size() - 1);
            }
            case "sum": {
                requireArgs(name, args, 1);
                List<Object> list = list(args.get(0), name);
                double total = 0.0;
                for (Object item : list) {
                    ctx.tick();
                    total += number(item, name);
                }
                return total;
            }
            case "mean": {
                requireArgs(name, args, 1);
                List<Object> list = list(args.get(0), name);
                if (list.isEmpty()) throw new ScriptException("mean: empty list");
                double total = 0.0;
                for (Object item : list) {
                    ctx.tick();
                    total += number(item, name);
                }
                return total / list.size();
            }
            case "range": {
                if (args.size() < 1 || args.size() > 3) {
                    throw new ScriptException("range: expected 1 to 3 arguments, got " + args.size());
                }
                long start;
                long stop;
                long step;
                if (args.size() == 1) {
                    start = 0;
                    stop = integer(args.get(0), "range");
                    step = 1;
                } else {
                    start = integer(args.get(0), "range");
                    stop = integer(args.get(1), "range");
                    step = args.size() == 3 ? integer(args.get(2), "range") : 1;
                }
                if (step == 0) throw new ScriptException("range: step must not be zero");
                List<Object> out = new ArrayList<>();
                long current = start;
                while ((step > 0 && current < stop) || (step < 0 && current > stop)) {
                    ctx.tick();
                    if (out.size() >= 100_000) throw new ScriptException("range: list size limit exceeded");
                    out.add((double) current);
                    current += step;
                }
                return out;
            }
            default:
                throw new ScriptException("unknown function '" + name + "'");
        }
    }

    private static boolean isBuiltinName(String name) {
        switch (name) {
            case "sqrt": case "abs": case "sin": case "cos": case "tan": case "log": case "exp":
            case "floor": case "ceil": case "round": case "pow": case "min": case "max": case "clamp":
            case "len": case "str": case "num": case "type": case "get": case "set": case "push":
            case "pop": case "sum": case "mean": case "range":
                return true;
            default:
                return false;
        }
    }

    private static void requireArgs(String name, List<Object> args, int expected) throws ScriptException {
        if (args.size() != expected) {
            throw new ScriptException(name + ": expected " + expected + " argument(s), got " + args.size());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value, String context) throws ScriptException {
        if (value instanceof List) return (List<Object>) value;
        throw new ScriptException(context + ": expected list, got " + typeName(value));
    }

    private static int index(Object value, int size, String context) throws ScriptException {
        long raw = integer(value, context);
        if (raw < 0 || raw >= size) throw new ScriptException(context + ": index out of range: " + raw);
        return (int) raw;
    }

    private static long integer(Object value, String context) throws ScriptException {
        double d = number(value, context);
        if (!Double.isFinite(d) || Math.floor(d) != d || d < Long.MIN_VALUE || d > Long.MAX_VALUE) {
            throw new ScriptException(context + ": expected integer");
        }
        return (long) d;
    }

    private static double number(Object value, String context) throws ScriptException {
        if (value instanceof Number) return ((Number) value).doubleValue();
        throw new ScriptException(context + ": expected number, got " + typeName(value));
    }

    private static int compare(Object a, Object b, String op) throws ScriptException {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b);
        }
        throw new ScriptException(op + ": comparable values must both be numbers or both be strings");
    }

    private static boolean equalValues(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0;
        }
        return a == null ? b == null : a.equals(b);
    }

    private static boolean truthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0.0;
        if (value instanceof String) return !((String) value).isEmpty();
        if (value instanceof List) return !((List<?>) value).isEmpty();
        return true;
    }

    private static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof Double) {
            double d = (Double) value;
            if (Double.isFinite(d) && Math.rint(d) == d && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
                return Long.toString((long) d);
            }
            return String.format(Locale.ROOT, "%s", d);
        }
        if (value instanceof List) {
            StringBuilder out = new StringBuilder("[");
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) out.append(", ");
                out.append(stringify(list.get(i)));
            }
            return out.append(']').toString();
        }
        return String.valueOf(value);
    }

    private static String typeName(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof String) return "string";
        if (value instanceof List) return "list";
        return value.getClass().getSimpleName();
    }
}
