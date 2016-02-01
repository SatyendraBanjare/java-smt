/*
 *  JavaSMT is an API wrapper for a collection of SMT solvers.
 *  This file is part of JavaSMT.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.solver.mathsat5;

import static org.sosy_lab.solver.mathsat5.Mathsat5NativeApi.msat_model_create_iterator;
import static org.sosy_lab.solver.mathsat5.Mathsat5NativeApi.msat_model_eval;
import static org.sosy_lab.solver.mathsat5.Mathsat5NativeApi.msat_model_iterator_has_next;
import static org.sosy_lab.solver.mathsat5.Mathsat5NativeApi.msat_model_iterator_next;
import static org.sosy_lab.solver.mathsat5.Mathsat5NativeApi.msat_term_is_true;
import static org.sosy_lab.solver.mathsat5.Mathsat5NativeApi.msat_term_repr;

import com.google.common.collect.UnmodifiableIterator;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.basicimpl.AbstractModel;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Mathsat5Model extends AbstractModel<Long, Long, Long> {

  private final long env;
  private final long model;

  private static final Pattern FLOATING_POINT_PATTERN = Pattern.compile("^(\\d+)_(\\d+)_(\\d+)$");
  private static final Pattern BITVECTOR_PATTERN = Pattern.compile("^(\\d+)_(\\d+)$");

  Mathsat5Model(long env, long model, Mathsat5FormulaCreator creator) {
    super(creator);
    this.env = env;
    this.model = model;
  }

  @Override
  public Object evaluateImpl(Long f) {
    long term = msat_model_eval(model, f);
    return convertValue(f, term);
  }

  @Override
  public Iterator<ValueAssignment> iterator() {
    return new Mathsat5ModelIterator();
  }

  private class Mathsat5ModelIterator extends UnmodifiableIterator<ValueAssignment> {

    // TODO: closing the iterator seems to trigger segfault when UFs are used.
    private final long modelIterator;
    private boolean closed = false;

    Mathsat5ModelIterator() {
      modelIterator = msat_model_create_iterator(model);
    }

    @Override
    public boolean hasNext() {

      return msat_model_iterator_has_next(modelIterator);
    }

    @Override
    public ValueAssignment next() {
      if (closed) {
        throw new NoSuchElementException();
      }

      long[] key = new long[1];
      long[] value = new long[1];
      if (msat_model_iterator_next(modelIterator, key, value)) {
        throw new NoSuchElementException();
      }
      Formula fKey = creator.encapsulateWithTypeOf(key[0]);
      Object fValue = convertValue(key[0], value[0]);

      return new ValueAssignment(fKey, fValue);
    }
  }

  private Object convertValue(long key, long term) {

    // To get the correct type, we generate it from the key, not the value.
    FormulaType<?> type = creator.getFormulaType(key);
    String repr = msat_term_repr(term);
    if (type.isBooleanType()) {
      return msat_term_is_true(env, term);
    } else if (type.isRationalType()) {
      return Rational.ofString(repr);
    } else if (type.isIntegerType()) {
      return new BigInteger(repr);
    } else if (type.isBitvectorType()) {
      return parseBitvector(repr);
    } else if (type.isFloatingPointType()) {
      return parseFloatingPoint(repr);
    } else {
      throw new IllegalArgumentException(
          "Mathsat term with unhandled type " + type + " for formula " + repr);
    }
  }

  private Number parseFloatingPoint(String lTermRepresentation) {

    // the term is of the format "<VALUE>_<EXPWIDTH>_<MANTWIDTH>"
    Matcher matcher = FLOATING_POINT_PATTERN.matcher(lTermRepresentation);
    if (!matcher.matches()) {
      throw new NumberFormatException("Unknown floating-point format: " + lTermRepresentation);
    }

    int expWidth = Integer.parseInt(matcher.group(2));
    int mantWidth = Integer.parseInt(matcher.group(3));

    if (expWidth == 11 && mantWidth == 52) {
      return Double.longBitsToDouble(UnsignedLong.valueOf(matcher.group(1)).longValue());
    } else if (expWidth == 8 && mantWidth == 23) {
      return Float.intBitsToFloat(UnsignedInteger.valueOf(matcher.group(1)).intValue());
    }

    // TODO to be fully correct, we would need to interpret this string
    return new BigInteger(matcher.group(1));
  }

  //TODO: change this to the latest version
  // (if possible try to use a BitvectorFormula instance here)
  private static BigInteger parseBitvector(String lTermRepresentation) {
    // the term is of the format "<VALUE>_<WIDTH>"
    Matcher matcher = BITVECTOR_PATTERN.matcher(lTermRepresentation);
    if (!matcher.matches()) {
      throw new NumberFormatException("Unknown bitvector format: " + lTermRepresentation);
    }

    // TODO: calculate negative value?
    String term = matcher.group(1);
    return new BigInteger(term);
  }
}
