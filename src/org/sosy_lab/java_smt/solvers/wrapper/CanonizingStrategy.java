/*
 *  JavaSMT is an API wrapper for a collection of SMT solvers.
 *  This file is part of JavaSMT.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.sosy_lab.java_smt.solvers.wrapper;

import java.util.List;
import org.sosy_lab.java_smt.api.FormulaManager;
import org.sosy_lab.java_smt.api.FormulaType;
import org.sosy_lab.java_smt.api.FunctionDeclarationKind;

public class CanonizingStrategy {

  public static CanonizingFormula canonizeInfixOperator(
      FormulaManager pMgr,
      FunctionDeclarationKind pOperator,
      CanonizingFormula pLeft,
      CanonizingFormula pRight,
      FormulaType<?> pReturnType) {
    FunctionDeclarationKind operator = pOperator;
    CanonizingFormula left = pLeft.canonize();
    CanonizingFormula right = pRight.canonize();

    if (isGreaterOp(pOperator)) {
      // TODO: find meaningful handling of arrays
      if (!pReturnType.isRationalType()) {
        operator = FunctionDeclarationKind.LT;
      } else {
        operator = FunctionDeclarationKind.LTE;
      }
      CanonizingFormula tmp = left;
      left = right;
      right = tmp;
    }

    // TODO: find meaningful handling of arrays
    if (isOrEqualOp(pOperator) && !pReturnType.isRationalType()) {
      CanonizingConstant epsilon = getMinimumSummand(pMgr, pReturnType);
      right = add(pMgr, right, epsilon, pReturnType);
    }

    CanonizingInfixOperator result = new CanonizingInfixOperator(pMgr, operator, pReturnType);
    result.add(left);
    result.add(right);

    return result;
  }

  private static CanonizingFormula add(
      FormulaManager pMgr,
      CanonizingFormula pFormula,
      CanonizingConstant pEpsilon,
      FormulaType<?> pReturnType) {
    FunctionDeclarationKind kind = null;
    CanonizingFormula result = null;

    if (pReturnType.isIntegerType()) {
      kind = FunctionDeclarationKind.ADD;
    }
    if (pReturnType.isBitvectorType()) {
      kind = FunctionDeclarationKind.BV_ADD;
    }
    if (pReturnType.isFloatingPointType()) {
      kind = FunctionDeclarationKind.FP_ADD;
    }

    if (pFormula instanceof CanonizingVariable) {
      CanonizingInfixOperator operator =
          new CanonizingInfixOperator(pMgr, pFormula.getParent(), kind, pReturnType);
      operator.add(pFormula);
      operator.add(pEpsilon);

      result = operator;
    } else if (pFormula instanceof CanonizingConstant) {
      Object value = null;

      if (pReturnType.isIntegerType() || pReturnType.isBitvectorType()) {
        value = ((long) ((CanonizingConstant) pFormula).getValue()) + ((int) pEpsilon.getValue());
      } else if (pReturnType.isFloatingPointType()) {
        if (pReturnType.equals(FormulaType.getSinglePrecisionFloatingPointType())) {
          value =
              ((float) ((CanonizingConstant) pFormula).getValue()) + ((float) pEpsilon.getValue());
        } else if (pReturnType.equals(FormulaType.getDoublePrecisionFloatingPointType())) {
          value =
              ((double) ((CanonizingConstant) pFormula).getValue())
                  + ((double) pEpsilon.getValue());
        } else {
          throw new IllegalArgumentException(
              "Type " + pReturnType + " is not fully implemented, yet.");
        }
      }
      result = new CanonizingConstant(pMgr, value, pReturnType);
    } else {
      result = add(pMgr, pFormula.getOperand1(), pEpsilon, pReturnType);
    }

    return result;
  }

  private static CanonizingConstant getMinimumSummand(
      FormulaManager pMgr, FormulaType<?> pReturnType) {
    if (pReturnType.isIntegerType() || pReturnType.isBitvectorType()) {
      return new CanonizingConstant(pMgr, Integer.valueOf(1), pReturnType);
    }
    if (pReturnType.isFloatingPointType()) {
      double value;
      if (pReturnType.equals(FormulaType.getSinglePrecisionFloatingPointType())) {
        // 1 / (2 ^ 149) is the smallest IEEE single precision floating point number, that is still
        // greater than 0
        value = Math.pow(2, -149);
      } else if (pReturnType.equals(FormulaType.getDoublePrecisionFloatingPointType())) {
        // 1 / (2 ^ 1074) is the smallest IEEE double precision floating point number, that is still
        // greater than 0
        value = Math.pow(2, -1074);
      } else {
        throw new IllegalArgumentException(
            "Type " + pReturnType + " is not fully implemented, yet.");
      }
      return new CanonizingConstant(pMgr, value, pReturnType);
    }
    if (pReturnType.isArrayType()) {
      // TODO: for arrays we have to traverse the correct operand to determine the type
    }
    if (pReturnType.isRationalType()) {
      // FIXME: for this one, there is no meaningful solution, so 'lessThanOrEquals' should be kept
      // as such
    }
    return null;
  }

  private static boolean isOrEqualOp(FunctionDeclarationKind pO) {
    boolean answer = false;
    if (pO == FunctionDeclarationKind.GTE
        || pO == FunctionDeclarationKind.FP_GE
        || pO == FunctionDeclarationKind.BV_SGE
        || pO == FunctionDeclarationKind.BV_UGE) {
      answer = true;
    }
    return answer;
  }

  private static boolean isGreaterOp(FunctionDeclarationKind pO) {
    boolean answer = false;
    if (pO == FunctionDeclarationKind.GT
        || pO == FunctionDeclarationKind.GTE
        || pO == FunctionDeclarationKind.FP_GT
        || pO == FunctionDeclarationKind.FP_GE
        || pO == FunctionDeclarationKind.BV_SGT
        || pO == FunctionDeclarationKind.BV_SGE
        || pO == FunctionDeclarationKind.BV_UGT
        || pO == FunctionDeclarationKind.BV_UGE) {
      answer = true;
    }
    return answer;
  }

  public static CanonizingFormula canonizeVariable(
      FormulaManager pMgr, String pName, FormulaType<?> pType) {
    String canonizedName = canonizeVariableName(pName);

    return new CanonizingVariable(pMgr, canonizedName, pType);
  }

  private static String canonizeVariableName(String pName) {
    // TODO: Implement some (hopefully) useful renaming strategy
    return pName;
  }

  public static CanonizingFormula canonizePrefixOperator(
      FormulaManager pMgr,
      FunctionDeclarationKind pOperator,
      List<CanonizingFormula> pOperands,
      int pOperandSize,
      FormulaType<?> pReturnType) {
    CanonizingPrefixOperator canonizedFormula =
        new CanonizingPrefixOperator(pMgr, pOperator, pOperandSize, pReturnType);
    for (CanonizingFormula operandToCanonize : pOperands) {
      canonizedFormula.add(operandToCanonize.canonize());
    }
    return canonizedFormula;
  }
}
