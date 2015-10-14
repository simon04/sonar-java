/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.se;

import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.LiteralTree;
import org.sonar.plugins.java.api.tree.ParenthesizedTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TypeCastTree;
import org.sonar.plugins.java.api.tree.UnaryExpressionTree;
import org.sonar.plugins.java.api.tree.VariableTree;

import javax.annotation.CheckForNull;

import java.util.List;

public class ConstraintManager {

//  private final Map<Symbol, SymbolicValue> symbolMap = new HashMap<>();
  private int counter = ProgramState.EMPTY_STATE.constraints.size();

  public SymbolicValue createSymbolicValue(Tree syntaxNode) {
//    SymbolicValue result = null;
//    if (syntaxNode.is(Tree.Kind.IDENTIFIER)) {
//      result = symbolMap.get(((IdentifierTree) syntaxNode).symbol());
//    }
//    if (result == null) {
//      result = new SymbolicValue.ObjectSymbolicValue(counter++);
//      if(syntaxNode.is(Tree.Kind.IDENTIFIER)) {
//        symbolMap.put(((IdentifierTree) syntaxNode).symbol(), result);
//      }
//    }
    if(syntaxNode.is(Tree.Kind.EQUAL_TO)) {
      return new SymbolicValue.EqualToSymbolicValue(counter++);
    } else if(syntaxNode.is(Tree.Kind.NOT_EQUAL_TO)) {
      return new SymbolicValue.NotEqualToSymbolicValue(counter++);
    }
    return new SymbolicValue.ObjectSymbolicValue(counter++);
  }

  public SymbolicValue supersedeSymbolicValue(VariableTree variable) {
    return createSymbolicValue(variable);
  }

  public SymbolicValue eval(ProgramState programState, Tree syntaxNode) {
    syntaxNode = skipTrivial(syntaxNode);
    switch (syntaxNode.kind()) {
      case ASSIGNMENT: {
        return eval(programState, ((AssignmentExpressionTree) syntaxNode).variable());
      }
      case NULL_LITERAL: {
        return SymbolicValue.NULL_LITERAL;
      }
      case BOOLEAN_LITERAL: {
        boolean value = Boolean.parseBoolean(((LiteralTree) syntaxNode).value());
        if (value) {
          return SymbolicValue.TRUE_LITERAL;
        }
        return SymbolicValue.FALSE_LITERAL;
      }
      case VARIABLE: {
        Symbol symbol = ((VariableTree) syntaxNode).symbol();
        SymbolicValue result = programState.values.get(symbol);
        if (result != null) {
          // symbolic value associated with local variable
          return result;
        }
        break;
      }
      case IDENTIFIER: {
        Symbol symbol = ((IdentifierTree) syntaxNode).symbol();
        SymbolicValue result = programState.values.get(symbol);
        if (result != null) {
          // symbolic value associated with local variable
          return result;
        }
        break;
      }
      case LOGICAL_COMPLEMENT: {
        UnaryExpressionTree unaryExpressionTree = (UnaryExpressionTree) syntaxNode;
        SymbolicValue val = eval(programState, unaryExpressionTree.expression());
        if (SymbolicValue.FALSE_LITERAL.equals(val)) {
          return SymbolicValue.TRUE_LITERAL;
        } else if (val.equals(SymbolicValue.TRUE_LITERAL)) {
          return SymbolicValue.FALSE_LITERAL;
        }
        // if not tied to a concrete value, create symbolic value with no constraint for now.
        // TODO : create constraint between expression and created symbolic value
      }
    }
    return createSymbolicValue(syntaxNode);
  }

  /**
   * Remove parenthesis and type cast.
   */
  private static Tree skipTrivial(Tree expression) {
    do {
      switch (expression.kind()) {
        case PARENTHESIZED_EXPRESSION:
          expression = ((ParenthesizedTree) expression).expression();
          break;
        case TYPE_CAST:
          expression = ((TypeCastTree) expression).expression();
          break;
        default:
          return expression;
      }
    } while (true);

  }

  public boolean isNull(ProgramState ps, SymbolicValue val) {
    return NullConstraint.NULL.equals(ps.constraints.get(val));
  }

  public Pair<ProgramState, ProgramState> assumeDual(ProgramState programState, Tree condition) {
    Pair<ProgramState, List<SymbolicValue>> unstack = ProgramState.unstack(programState, 1);
    SymbolicValue sv = unstack.b.get(0);
    return new Pair<>(setConstraint(unstack.a, sv, BooleanConstraint.FALSE), setConstraint(unstack.a, sv, BooleanConstraint.TRUE));
/*

    //FIXME condition value should be evaluated to determine if it is worth exploring this branch. This should probably be done in a dedicated checker.
>>>>>>> SONARJAVA-1311 Better handling of checker dispatch
    condition = skipTrivial(condition);
    switch (condition.kind()) {
      case INSTANCE_OF: {
        InstanceOfTree instanceOfTree = (InstanceOfTree) condition;
        SymbolicValue exprValue = eval(programState, instanceOfTree.expression());
        if (isNull(programState, exprValue)) {
          return new Pair<>(programState, null);
        }
        // if instanceof is true then we know for sure that expression is not null.
        return new Pair<>(programState, setConstraint(programState, exprValue, NullConstraint.NOT_NULL));
      }
      case EQUAL_TO: {
        BinaryExpressionTree equalTo = (BinaryExpressionTree) condition;
        SymbolicValue lhs = eval(programState, equalTo.leftOperand());
        SymbolicValue rhs = eval(programState, equalTo.rightOperand());
        if (isNull(programState, lhs)) {
          ProgramState stateNull = setConstraint(programState, rhs, NullConstraint.NULL);
          ProgramState stateNotNull = setConstraint(programState, rhs, NullConstraint.NOT_NULL);
          return new Pair<>(stateNotNull, stateNull);
        } else if (isNull(programState, rhs)) {
          ProgramState stateNull = setConstraint(programState, lhs, NullConstraint.NULL);
          ProgramState stateNotNull = setConstraint(programState, lhs, NullConstraint.NOT_NULL);
          return new Pair<>(stateNotNull, stateNull);
        }
        break;
      }
      case NOT_EQUAL_TO: {
        BinaryExpressionTree notEqualTo = (BinaryExpressionTree) condition;
        SymbolicValue lhs = eval(programState, notEqualTo.leftOperand());
        SymbolicValue rhs = eval(programState, notEqualTo.rightOperand());
        if (isNull(programState, lhs)) {
          ProgramState stateNull = setConstraint(programState, rhs, NullConstraint.NULL);
          ProgramState stateNotNull = setConstraint(programState, rhs, NullConstraint.NOT_NULL);
          return new Pair<>(stateNull, stateNotNull);
        } else if (isNull(programState, rhs)) {
          ProgramState stateNull = setConstraint(programState, lhs, NullConstraint.NULL);
          ProgramState stateNotNull = setConstraint(programState, lhs, NullConstraint.NOT_NULL);
          return new Pair<>(stateNull, stateNotNull);
        }
        break;
      }
      case LOGICAL_COMPLEMENT:
        return assumeDual(programState, ((UnaryExpressionTree) condition).expression()).invert();
      case CONDITIONAL_OR:
      case CONDITIONAL_AND:
        // this is the case for branches such as "if (lhs && rhs)" and "if (lhs || rhs)"
        // we already made an assumption on lhs, because CFG contains branch for it, so now let's make an assumption on rhs
        BinaryExpressionTree binaryExpressionTree = (BinaryExpressionTree) condition;
        return assumeDual(programState, binaryExpressionTree.rightOperand());
      case BOOLEAN_LITERAL:
        LiteralTree literalTree = ((LiteralTree) condition);
        if ("true".equals(literalTree.value())) {
          return new Pair<>(null, programState);
        }
        return new Pair<>(programState, null);
      case IDENTIFIER:
        IdentifierTree id = (IdentifierTree) condition;
        SymbolicValue eval = eval(programState, id);
        return new Pair<>(setConstraint(programState, eval, BooleanConstraint.FALSE), setConstraint(programState, eval, BooleanConstraint.TRUE));
    }
    return new Pair<>(programState, programState);
    */
  }

  @CheckForNull
  static ProgramState setConstraint(ProgramState programState, SymbolicValue sv, BooleanConstraint booleanConstraint) {
    Object data = programState.constraints.get(sv);
    // update program state only for a different constraint
    if (data instanceof BooleanConstraint) {
      BooleanConstraint bc = (BooleanConstraint) data;
      if ((BooleanConstraint.TRUE.equals(booleanConstraint) && BooleanConstraint.FALSE.equals(bc)) ||
        (BooleanConstraint.TRUE.equals(bc) && BooleanConstraint.FALSE.equals(booleanConstraint))) {
        // setting null where value is known to be non null or the contrary
        return null;
      }
    }
    if (data == null || !data.equals(booleanConstraint)) {
      return sv.setConstraint(programState, booleanConstraint);
//      if(sv instanceof SymbolicValue.EqualToSymbolicValue) {
//        SymbolicValue.EqualToSymbolicValue equalToSymbolicValue = (SymbolicValue.EqualToSymbolicValue) sv;
//        if(equalToSymbolicValue.leftOp.equals(equalToSymbolicValue.rightOp)) {
//          return BooleanConstraint.TRUE.equals(booleanConstraint) ? programState : null;
//        }
//        programState = copyConstraint(equalToSymbolicValue.leftOp, equalToSymbolicValue.rightOp, programState, booleanConstraint);
//        if(programState == null) {
//          return null;
//        }
//        programState = copyConstraint(equalToSymbolicValue.rightOp, equalToSymbolicValue.leftOp, programState, booleanConstraint);
//      } else {
//        // store constraint only if symbolic value can be reached by a symbol.
//        if(programState.values.containsValue(sv)) {
//          Map<SymbolicValue, Object> temp = Maps.newHashMap(programState.constraints);
//          temp.put(sv, booleanConstraint);
//          return new ProgramState(programState.values, temp, programState.visitedPoints, programState.stack);
//        }
//      }
    }
    return programState;
  }

//  private static ProgramState copyConstraint(SymbolicValue from, SymbolicValue to, ProgramState programState, BooleanConstraint booleanConstraint) {
//    ProgramState result = programState;
//    Object constraintLeft = programState.constraints.get(from);
//    if(constraintLeft instanceof BooleanConstraint) {
//      BooleanConstraint boolConstraint = (BooleanConstraint) constraintLeft;
//      result = setConstraint(programState, to, BooleanConstraint.TRUE.equals(booleanConstraint) ? boolConstraint : boolConstraint.inverse());
//    } else if(constraintLeft instanceof NullConstraint) {
//      NullConstraint nullConstraint = (NullConstraint) constraintLeft;
//      result = setConstraint(programState, to, BooleanConstraint.TRUE.equals(booleanConstraint) ? nullConstraint : nullConstraint.inverse());
//    }
//    return result;
//  }

  @CheckForNull
  static ProgramState setConstraint(ProgramState programState, SymbolicValue sv, NullConstraint nullConstraint) {
    Object data = programState.constraints.get(sv);
    // update program state only for a different constraint
    if (data instanceof NullConstraint) {
      NullConstraint nc = (NullConstraint) data;
      if ((NullConstraint.NULL.equals(nullConstraint) && NullConstraint.NOT_NULL.equals(nc)) ||
        (NullConstraint.NULL.equals(nc) && NullConstraint.NOT_NULL.equals(nullConstraint))) {
        // setting null where value is known to be non null or the contrary
        return null;
      }
    }
    if (data == null || !data.equals(nullConstraint)) {
     return sv.setConstraint(programState, nullConstraint);
    }
    return programState;
  }

  public enum NullConstraint {
    NULL,
    NOT_NULL;
    NullConstraint inverse() {
      if(NULL == this) {
        return NOT_NULL;
      }
      return NULL;
    }

  }

  public enum BooleanConstraint {
    TRUE,
    FALSE;
    BooleanConstraint inverse() {
      if(TRUE == this) {
        return FALSE;
      }
      return TRUE;
    }
  }
}