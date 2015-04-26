/*
 *  Copyright 2015 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.flavour.expr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.parboiled.Action;
import org.parboiled.BaseParser;
import org.parboiled.Context;
import org.parboiled.Rule;
import org.parboiled.support.Var;
import org.teavm.flavour.expr.ExprParser.Holder;
import org.teavm.flavour.expr.ast.*;
import org.teavm.flavour.expr.type.*;

/**
 *
 * @author Alexey Andreev
 */
class ExprParser extends BaseParser<Holder> {
    private static final double[] positiveExponents = { 1E1, 1E2, 1E4, 1E8, 1E16, 1E32, 1E64, 1E128 };
    private static final double[] negativeExponents = { 1E-1, 1E-2, 1E-4, 1E-8, 1E-16, 1E-32, 1E-64, 1E-128 };
    ClassResolver classResolver;

    Rule Root() {
        return Sequence(Expression(), EOI);
    }

    Rule Expression() {
        return Or();
    }

    Rule Or() {
        return Sequence(
            And(),
            ZeroOrMore(
                Keyword("or"),
                And(),
                push(wrap(new BinaryExpr<>(pop(1).expr, pop().expr, BinaryOperation.OR))),
                setLocations));
    }

    Rule And() {
        return Sequence(
            Not(),
            ZeroOrMore(
                Keyword("and"),
                Not(),
                push(wrap(new BinaryExpr<>(pop(1).expr, pop().expr, BinaryOperation.AND))),
                setLocations));
    }

    Rule Not() {
        return FirstOf(
            Comparison(),
            Sequence(
                Keyword("not"),
                Not(),
                push(wrap(new UnaryExpr<>(pop().expr, UnaryOperation.NOT))),
                setLocations));
    }

    Rule Comparison() {
        Var<BinaryOperation> op = new Var<>();
        return Sequence(
            Additive(),
            ZeroOrMore(
                FirstOf(
                    Sequence(Keyword("=="), op.set(BinaryOperation.EQUAL)),
                    Sequence(Keyword("!="), op.set(BinaryOperation.NOT_EQUAL)),
                    Sequence(OneOfKeyword("<", "less"), op.set(BinaryOperation.LESS)),
                    Sequence(OneOfKeyword("<=", "loe"), op.set(BinaryOperation.LESS_OR_EQUAL)),
                    Sequence(OneOfKeyword(">", "greater"), op.set(BinaryOperation.GREATER)),
                    Sequence(OneOfKeyword(">=", "goe"), op.set(BinaryOperation.GREATER_OR_EQUAL))),
                Additive(),
                push(wrap(new BinaryExpr<>(pop(1).expr, pop().expr, op.get()))),
                setLocations));
    }

    Rule Additive() {
        Var<BinaryOperation> op = new Var<>();
        return Sequence(
            Multiplicative(),
            ZeroOrMore(
                FirstOf(
                    Sequence(Keyword("+"), op.set(BinaryOperation.ADD)),
                    Sequence(Keyword("-"), op.set(BinaryOperation.SUBTRACT))),
                Multiplicative(),
                push(wrap(new BinaryExpr<>(pop(1).expr, pop().expr, op.get()))),
                setLocations));
    }

    Rule Multiplicative() {
        Var<BinaryOperation> op = new Var<>();
        return Sequence(Arithmetic(),
            ZeroOrMore(
                FirstOf(
                    Sequence(Keyword("*"), op.set(BinaryOperation.MULTIPLY)),
                    Sequence(Keyword("/"), op.set(BinaryOperation.DIVIDE)),
                    Sequence(Keyword("%"), op.set(BinaryOperation.REMAINDER))),
                Arithmetic(),
                push(wrap(new BinaryExpr<>(pop(1).expr, pop().expr, op.get()))),
                setLocations));
    }

    Rule Arithmetic() {
        return FirstOf(
            Path(),
            Sequence(
                Keyword("-"),
                Arithmetic(),
                push(wrap(new UnaryExpr<>(pop().expr, UnaryOperation.NEGATE))),
                setLocations));
    }

    Rule Path() {
        return FirstOf(
            Cast(),
            Sequence(Primitive(), ZeroOrMore(Navigation()), Optional(InstanceOf())));
    }

    Rule Cast() {
        return Sequence(
            Keyword("("),
            Type(),
            Keyword(")"),
            Primitive(),
            push(wrap(new CastExpr<>(pop().expr, pop().type))));
    }

    Rule InstanceOf() {
        return Sequence(
            Keyword("instanceof"),
            GenericType(),
            push(wrap(new InstanceOfExpr<>(pop(1).expr, (GenericType)pop().type))));
    }

    Rule Type() {
        return Sequence(NonArrayType(), ArraySuffix());
    }

    Rule GenericType() {
        return Sequence(QualifiedClassType(), ArraySuffix());
    }

    Rule ArraySuffix() {
        return ZeroOrMore(
            Keyword("["),
            Keyword("]"),
            push(wrap(new GenericArray(pop().type))));
    }

    Rule NonArrayType() {
        return FirstOf(
            Sequence(Keyword("boolean"), push(wrap(Primitive.BOOLEAN))),
            Sequence(Keyword("char"), push(wrap(Primitive.CHAR))),
            Sequence(Keyword("byte"), push(wrap(Primitive.BYTE))),
            Sequence(Keyword("short"), push(wrap(Primitive.SHORT))),
            Sequence(Keyword("int"), push(wrap(Primitive.INT))),
            Sequence(Keyword("long"), push(wrap(Primitive.LONG))),
            Sequence(Keyword("float"), push(wrap(Primitive.FLOAT))),
            Sequence(Keyword("double"), push(wrap(Primitive.DOUBLE))),
            QualifiedClassType());
    }

    Rule QualifiedClassType() {
        Var<String> className = new Var<>();
        Var<List<GenericType>> typeArgs = new Var<>();
        return Sequence(
            RawClassType(className),
            typeArgs.set(new ArrayList<GenericType>()),
            Optional(
                Keyword("<"),
                TypeArguments(typeArgs),
                Keyword(">")),
            push(wrap(new GenericClass(className.get(), typeArgs.get()))));
    }

    Rule RawClassType(Var<String> className) {
        Var<StringBuilder> sb = new Var<>();
        Var<String> idPart = new Var<>();
        return Sequence(
            Sequence(Identifier(idPart), sb.set(new StringBuilder()), ACTION(sb.get().append(idPart.get()) != null)),
            ZeroOrMore(
                Keyword("."),
                Sequence(Identifier(idPart), ACTION(sb.get().append(".").append(idPart.get()) != null))),
            className.set(sb.get().toString()));
    }

    Rule TypeArguments(Var<List<GenericType>> typeArgs) {
        return Sequence(
            GenericType(),
            typeArgs.get().add((GenericType)pop().type),
            ZeroOrMore(
                Keyword(","),
                typeArgs.get().add((GenericType)pop().type)));
    }

    Rule Primitive() {
        return FirstOf(
            Number(),
            StringLiteral(),
            Sequence(Keyword("true"), push(wrap(new ConstantExpr<Void>(true))), setLocations),
            Sequence(Keyword("false"), push(wrap(new ConstantExpr<Void>(false))), setLocations),
            Sequence(Keyword("null"), push(wrap(new ConstantExpr<Void>(null))), setLocations),
            Identifier(),
            Sequence(Keyword("("), Expression(), Keyword(")")));
    }

    Rule Navigation() {
        return FirstOf(Qualification(), ArraySubscript());
    }

    Rule Qualification() {
        Var<Expr<Void>> instance = new Var<>();
        Var<String> property = new Var<>();
        Var<List<Expr<Void>>> list = new Var<>();
        return Sequence(
            instance.set(pop().expr),
            Keyword("."),
            Identifier(property),
            list.set(null),
            Optional(
                Keyword("("),
                list.set(new ArrayList<Expr<Void>>()),
                ExpressionList(list),
                Keyword(")")),
            qualify(instance, property, list),
            setLocations);
    }

    Rule ExpressionList(Var<List<Expr<Void>>> list) {
        return Optional(Sequence(Expression(), append(list), ZeroOrMore(Keyword(","), Expression(), append(list))));
    }

    Rule ArraySubscript() {
        Var<Expr<Void>> array = new Var<>();
        Var<Expr<Void>> index = new Var<>();
        return Sequence(
            array.set(pop().expr),
            Keyword("["),
            Expression(),
            index.set(pop().expr),
            Keyword("]"),
            push(wrap(new BinaryExpr<>(array.get(), index.get(), BinaryOperation.GET_ELEMENT))),
            setLocations);
    }

    Rule Keyword(String delimiter) {
        return Sequence(String(delimiter), Whitespace());
    }

    Rule OneOfKeyword(String... delimiters) {
        Rule[] rules = new Rule[delimiters.length];
        for (int i = 0; i < delimiters.length; ++i) {
            rules[i] = Keyword(delimiters[i]);
        }
        return FirstOf(rules);
    }

    Rule Identifier() {
        Var<String> id = new Var<>();
        return Sequence(Identifier(id), push(wrap(new VariableExpr<Void>(id.get()))), setLocations);
    }

    Rule Identifier(Var<String> id) {
        return Sequence(
            Sequence(
                IdentifierStart(),
                ZeroOrMore(IdentifierPart())),
            id.set(match()),
            Whitespace());
    }

    Rule IdentifierStart() {
        return FirstOf(CharRange('A', 'Z'), CharRange('a', 'z'), Ch('_'));
    }

    Rule IdentifierPart() {
        return FirstOf(IdentifierStart(), CharRange('0', '9'));
    }

    Rule Number() {
        return Sequence(FirstOf(IntNumber(), FracNumber()), Whitespace());
    }

    Rule IntNumber() {
        final Var<String> digits = new Var<>();
        Action<Holder> action = new Action<Holder>() {
            @Override public boolean run(Context<Holder> context) {
                try {
                    int value = Integer.parseInt(digits.get());
                    context.getValueStack().push(wrap(new ConstantExpr<Void>(value)));
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        };
        return Sequence(IntNumber(digits), action, setLocations);
    }

    Rule IntNumber(Var<String> s) {
        Var<String> firstDigit = new Var<>();
        return FirstOf(
            Sequence(Ch('0'), s.set("0")),
            Sequence(CharRange('1', '9'),
                s.set(match()),
                Optional(
                    firstDigit.set(s.get()),
                    Digits(s),
                    s.set(firstDigit.get() + match()))));
    }

    Rule FracNumber() {
        final Var<String> intPart = new Var<>();
        final Var<String> fracPart = new Var<>();
        final Var<Character> exponentSign = new Var<>();
        final Var<String> exponent = new Var<>();
        Action<Expr<Void>> action = new Action<Expr<Void>>() {
            @Override public boolean run(Context<Expr<Void>> context) {
                return stringToDouble(context, intPart.get(), fracPart.get(), exponentSign.get(), exponent.get());
            }
        };
        return Sequence(FirstOf(
                Sequence(IntNumber(intPart), Ch('.'), Digits(fracPart), Optional(Exponent(exponent, exponentSign))),
                Sequence(IntNumber(intPart), Exponent(exponent, exponentSign))),
                action, setLocations);
    }

    Rule Exponent(Var<String> s, Var<Character> sign) {
        return Sequence(IgnoreCase('e'), Optional(AnyOf("+-")), sign.set(currentChar()), Digits(s));
    }

    Rule Digits(Var<String> s) {
        return Sequence(OneOrMore(Digit()), s.set(match()));
    }

    Rule StringLiteral() {
        Var<StringBuilder> sb = new Var<>();
        Var<Character> ch = new Var<>();
        return Sequence(Ch('\''),
            sb.set(new StringBuilder()),
            ZeroOrMore(StringLiteralChar(ch), append(sb, ch)),
            Ch('\''),
            push(wrap(new ConstantExpr<Void>(sb.get().toString()))),
            setLocations,
            Whitespace());
    }

    Rule StringLiteralChar(Var<Character> ch) {
        return FirstOf(
            Sequence(
                FirstOf(
                    CharRange('\u001F', '&'),
                    CharRange('(', '['), CharRange(']', Character.MAX_VALUE)),
                ch.set(matchedChar())),
            Sequence(String("\\"), StringEscapeSequence(ch)));
    }

    Rule StringEscapeSequence(Var<Character> ch) {
        return FirstOf(
            Sequence(Ch('r'), ch.set('\r')),
            Sequence(Ch('n'), ch.set('\n')),
            Sequence(Ch('t'), ch.set('\t')),
            Sequence(Ch('\''), ch.set('\'')),
            Sequence(Ch('\\'), ch.set('\\')),
            Sequence(IgnoreCase('u'), NTimes(4, HexDigit()), ch.set((char)Integer.parseInt(match(), 16))));
    }

    Rule HexDigit() {
        return FirstOf(Digit(), CharRange('a', 'f'), CharRange('A', 'F'));
    }

    Rule Digit() {
        return CharRange('0', '9');
    }

    Rule Whitespace() {
        return ZeroOrMore(WhitespaceChar());
    }

    Rule WhitespaceChar() {
        return AnyOf(" \t\r\n");
    }

    private boolean stringToDouble(Context<Expr<Void>> context,  String integer, String fractional,
            Character exponentSign, String exponent) {
        StringBuilder digits = new StringBuilder(integer);
        if (fractional != null) {
            digits.append(fractional);
        }
        int exponentNum;
        try {
            exponentNum = Integer.parseInt(exponent);
        } catch (NumberFormatException e) {
            return false;
        }
        if (exponentNum > 308) {
            return false;
        }
        if (exponentSign != null && exponentSign.charValue() == '-') {
            exponentNum = -exponentNum;
        }
        exponentNum += integer.length();
        if (digits.length() > 16) {
            digits.setLength(16);
        } else {
            while (digits.length() < 16) {
                digits.append(' ');
            }
        }
        long mantissa = Long.parseLong(digits.toString());
        context.getValueStack().push(new ConstantExpr<Void>(fastExponent(mantissa, exponentNum - 16)));
        return true;
    }

    private double fastExponent(long mantissa, int exponent) {
        double result = mantissa;
        double[] exponents = exponent >= 0 ? positiveExponents : negativeExponents;
        int bit = 1;
        for (int i = 0; i < exponents.length; ++i) {
            if ((exponent |= bit) != 0) {
                result *= exponents[i];
            }
            bit <<= 1;
        }
        return result;
    }

    Action<Holder> setLocations = new Action<Holder>() {
        @Override
        public boolean run(Context<Holder> context) {
            Expr<Void> expr = context.getValueStack().peek().expr;
            if (expr != null) {
                expr.setStart(context.getMatchStartIndex());
                expr.setEnd(context.getMatchEndIndex());
            }
            return true;
        }
    };

    Action<Holder> append(final Var<StringBuilder> sb, final Var<Character> ch) {
        return new Action<Holder>() {
            @Override
            public boolean run(Context<Holder> context) {
                sb.get().append(ch.get().charValue());
                return true;
            }
        };
    }

    Action<Holder> qualify(final Var<Expr<Void>> instanceVar, final Var<String> propertyVar,
            final Var<List<Expr<Void>>> argumentsVar) {
        return new Action<Holder>() {
            @Override
            public boolean run(Context<Holder> context) {
                Expr<Void> instance = instanceVar.get();
                String className = isClassName(instance);
                if (argumentsVar.get() == null) {
                    if (className == null) {
                        context.getValueStack().push(wrap(new PropertyExpr<>(instance, propertyVar.get())));
                    } else {
                        context.getValueStack().push(wrap(new StaticPropertyExpr<Void>(className, propertyVar.get())));
                    }
                } else {
                    if (className == null) {
                        context.getValueStack().push(wrap(new InvocationExpr<>(instance, propertyVar.get(),
                                argumentsVar.get())));
                    } else {
                        context.getValueStack().push(wrap(new StaticInvocationExpr<>(className, propertyVar.get(),
                                argumentsVar.get())));
                    }
                }
                return true;
            }
        };
    }

    Action<Holder> append(final Var<List<Expr<Void>>> list) {
        return new Action<Holder>() {
            @Override
            public boolean run(Context<Holder> context) {
                list.get().add(context.getValueStack().pop().expr);
                return true;
            }
        };
    }

    private String isClassName(Expr<Void> expr) {
        List<String> parts = new ArrayList<>();
        while (expr instanceof PropertyExpr<?>) {
            PropertyExpr<Void> property = (PropertyExpr<Void>)expr;
            parts.add(property.getPropertyName());
            expr = property.getInstance();
        }
        if (!(expr instanceof VariableExpr<?>)) {
            return null;
        }
        VariableExpr<Void> root = (VariableExpr<Void>)expr;
        parts.add(root.getName());
        Collections.reverse(parts);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); ++i) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(parts.get(i));
        }
        return classResolver.findClass(sb.toString());
    }

    static Holder wrap(Expr<Void> expr) {
        Holder holder = new Holder();
        holder.expr = expr;
        return holder;
    }

    static Holder wrap(ValueType type) {
        Holder holder = new Holder();
        holder.type = type;
        return holder;
    }

    static class Holder {
        Expr<Void> expr;
        ValueType type;
    }
}
