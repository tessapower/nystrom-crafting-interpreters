package com.craftinginterpreters.lox;

import java.util.List;
import java.util.function.Supplier;

import static com.craftinginterpreters.lox.TokenType.*;

public class Parser {
    private static class ParseError extends RuntimeException {}
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    private Expr expression() {
        return equality();
    }

    private Expr equality() {
        return foldExpression(this::comparison, BANG_EQUAL, EQUAL_EQUAL);
    }

    private Expr comparison() {
        return foldExpression(this::term, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL);
    }

    private Expr term() {
        return foldExpression(this::factor, MINUS, PLUS);
    }

    private Expr factor() {
        return foldExpression(this::unary, SLASH, STAR);
    }

    /**
     * Folds a left-associative series of binary operators given a list of token types and
     * an operand method handle into one expression.
     */
    private Expr foldExpression(Supplier<Expr> operandMethod, TokenType... types) {
        Expr expr = operandMethod.get();

        // Check that the current token matches any of the given types.
        // Each iteration we move from left to right, we create a new
        // binary expression using the previous one as the left operand,
        // e.g. a == b == c == d, starts with a and compares moving right
        while (match(types)) {
            Token operator = previous();
            Expr right = operandMethod.get();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();

            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    private boolean match(TokenType... types) {
        for (var type : types) {
            if (check(type)) {
                advance();

                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;

        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;

        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);

        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch(peek().type) {
                case CLASS, FUN, VAR, FOR, IF, WHILE, PRINT, RETURN -> {
                    return;
                }
            }

            advance();
        }
    }
}
