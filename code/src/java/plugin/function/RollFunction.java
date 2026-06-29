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

import java.util.Optional;

import pcgen.base.formatmanager.FormatUtilities;
import pcgen.base.formula.base.DependencyManager;
import pcgen.base.formula.base.EvaluationManager;
import pcgen.base.formula.base.FormulaFunction;
import pcgen.base.formula.base.FormulaSemantics;
import pcgen.base.formula.function.FunctionUtilities;
import pcgen.base.formula.parse.Node;
import pcgen.base.formula.visitor.DependencyVisitor;
import pcgen.base.formula.visitor.EvaluateVisitor;
import pcgen.base.formula.visitor.SemanticsVisitor;
import pcgen.base.formula.visitor.StaticVisitor;
import pcgen.base.util.FormatManager;
import pcgen.core.RollingMethods;

/**
 * RollFunction is the new-formula-system counterpart of the JEP {@code RollCommand}.
 * It evaluates a dice expression such as {@code roll("3d6")} by delegating to
 * {@link RollingMethods#roll(String)}, so dice-syntax parity with the legacy engine
 * is structural.
 *
 * <p>The single argument must be a quoted-string <em>literal</em> — the load-time
 * check enforces only that shape (via {@link FunctionUtilities#ensureQuotedString}),
 * not the dice grammar inside the quotes. A malformed dice expression such as
 * {@code roll("xyz")} therefore parses cleanly at load time and fails the same way
 * the legacy JEP path does: {@link RollingMethods#roll(String)} logs an error and
 * returns 0. The win versus the legacy path is that non-literal argument shapes
 * (missing arg, extra arg, bare identifier, expression) are rejected at LST load.
 *
 * <p>To relax the literal-only restriction in a follow-up (e.g. {@code roll(diceVar)}
 * against a {@code STRING} variable), drop the {@link FunctionUtilities#ensureQuotedString}
 * call in {@link #allowArgs} in favour of a {@code STRING_MANAGER} format check, and
 * use {@code args[0].jjtAccept(visitor, manager)} in {@link #evaluate} to resolve
 * the variable at evaluation time.
 */
public class RollFunction implements FormulaFunction
{

	@Override
	public String getFunctionName()
	{
		return "ROLL";
	}

	@Override
	public Boolean isStatic(StaticVisitor visitor, Node[] args)
	{
		// A roll involves the RNG, so it is never static.
		return Boolean.FALSE;
	}

	@Override
	public FormatManager<?> allowArgs(SemanticsVisitor visitor, Node[] args,
		FormulaSemantics semantics)
	{
		FunctionUtilities.validateArgCount(this, args, 1);
		FunctionUtilities.ensureQuotedString(args[0], 1);
		return FormatUtilities.NUMBER_MANAGER;
	}

	@Override
	public Object evaluate(EvaluateVisitor visitor, Node[] args,
		EvaluationManager manager)
	{
		String diceExpression = FunctionUtilities.getQuotedString(args[0], 1);
		return RollingMethods.roll(diceExpression);
	}

	@Override
	public Optional<FormatManager<?>> getDependencies(DependencyVisitor visitor,
		DependencyManager manager, Node[] args)
	{
		return Optional.of(FormatUtilities.NUMBER_MANAGER);
	}
}
