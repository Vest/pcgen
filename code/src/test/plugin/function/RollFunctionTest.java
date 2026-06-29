/*
 * Copyright 2026 (C) Vest <Vest@users.noreply.github.com>
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package plugin.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import pcgen.base.formula.base.EvaluationManager;
import pcgen.base.formula.parse.SimpleNode;
import pcgen.base.formula.visitor.EvaluateVisitor;
import pcgen.base.formula.visitor.ReconstructionVisitor;
import plugin.function.testsupport.AbstractFormulaTestCase;
import plugin.function.testsupport.TestUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RollFunctionTest extends AbstractFormulaTestCase
{

	@BeforeEach
	@Override
	public void setUp() throws Exception
	{
		super.setUp();
		getFunctionLibrary().addFunction(new RollFunction());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"roll()",                       // missing argument
		"roll(\"3d6\", \"extra\")",      // too many arguments
		"roll(3)",                       // non-string argument
		"roll(diceVar)",                 // bare identifier — not a quoted-string literal
		"roll(\"1d6\" + \"2d6\")"       // string concatenation — not a literal
	})
	void testInvalidArguments(String formula)
	{
		SimpleNode node = TestUtilities.doParse(formula);
		assertNotNull(node, "Formula must parse before semantic validation");
		isNotValid(formula, node);
	}

	@Test
	void testIsNotStatic()
	{
		// A roll is never static: even with a literal dice expression
		// the RNG produces a different value each evaluation.
		String formula = "roll(\"1d6\")";
		SimpleNode node = TestUtilities.doParse(formula);
		assertNotNull(node);
		isValid(node, numberManager, null);
		isStatic(formula, node, false);
	}

	@Test
	void testFixedSingle()
	{
		// "1" has no 'd' so RollingMethods returns the integer literal.
		String formula = "roll(\"1\")";
		SimpleNode node = TestUtilities.doParse(formula);
		assertNotNull(node);
		isValid(node, numberManager, null);
		evaluatesTo(formula, node, Integer.valueOf(1));
	}

	@Test
	void testFixedSum()
	{
		String formula = "roll(\"3\")";
		SimpleNode node = TestUtilities.doParse(formula);
		assertNotNull(node);
		isValid(node, numberManager, null);
		evaluatesTo(formula, node, Integer.valueOf(3));
	}

	@Test
	void testRangeSingleDie()
	{
		String formula = "roll(\"1d6\")";
		SimpleNode node = TestUtilities.doParse(formula);
		isValid(node, numberManager, null);
		EvaluateVisitor visitor = new EvaluateVisitor();
		EvaluationManager manager = generateManager();
		for (int i = 0; i < 100; i++)
		{
			Object result = visitor.visit(node, manager);
			int value = ((Number) result).intValue();
			assertTrue(value >= 1 && value <= 6,
				() -> "1d6 produced " + result + " (out of range)");
		}
	}

	@Test
	void testRangeMultiDie()
	{
		String formula = "roll(\"3d6\")";
		SimpleNode node = TestUtilities.doParse(formula);
		isValid(node, numberManager, null);
		EvaluateVisitor visitor = new EvaluateVisitor();
		EvaluationManager manager = generateManager();
		for (int i = 0; i < 100; i++)
		{
			Object result = visitor.visit(node, manager);
			int value = ((Number) result).intValue();
			assertTrue(value >= 3 && value <= 18,
				() -> "3d6 produced " + result + " (out of range)");
		}
	}

	@Test
	void testReconstruction()
	{
		String formula = "roll(\"3d6\")";
		SimpleNode node = TestUtilities.doParse(formula);
		isValid(node, numberManager, null);
		Object rv = new ReconstructionVisitor().visit(node, new StringBuilder());
		assertEquals(formula, rv.toString());
	}
}
