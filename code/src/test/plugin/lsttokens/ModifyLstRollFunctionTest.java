/*
 * Copyright 2026 (C) Vest <Vest@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */
package plugin.lsttokens;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URISyntaxException;

import pcgen.base.util.FormatManager;
import pcgen.cdom.base.VarContainer;
import pcgen.cdom.base.VarHolder;
import pcgen.cdom.formula.scope.PCGenScope;
import pcgen.core.PCTemplate;
import pcgen.persistence.PersistenceLayerException;
import pcgen.rules.context.LoadContext;
import pcgen.rules.persistence.CDOMLoader;
import pcgen.rules.persistence.token.CDOMToken;
import pcgen.rules.persistence.token.CDOMWriteToken;
import plugin.function.RollFunction;
import plugin.lsttokens.testsupport.AbstractGlobalTokenTestCase;
import plugin.lsttokens.testsupport.CDOMTokenLoader;
import plugin.lsttokens.testsupport.ConsolidationRule;
import plugin.lsttokens.testsupport.TokenRegistration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code roll(...)} composes cleanly with the {@code MODIFY:} LST
 * token: a quoted-string dice expression parses and round-trips, while a bare
 * (unquoted) identifier as the dice argument fails at LST load.
 */
class ModifyLstRollFunctionTest extends AbstractGlobalTokenTestCase
{
	private static final ModifyLst TOKEN = new ModifyLst();
	private static final CDOMTokenLoader<PCTemplate> LOADER = new CDOMTokenLoader<>();

	@BeforeEach
	@Override
	public void setUp() throws PersistenceLayerException, URISyntaxException
	{
		super.setUp();
		TokenRegistration.register(new plugin.modifier.number.AddModifierFactory());
	}

	@Override
	public CDOMLoader<PCTemplate> getLoader()
	{
		return LOADER;
	}

	@Override
	public Class<PCTemplate> getCDOMClass()
	{
		return PCTemplate.class;
	}

	@Override
	public CDOMToken<VarHolder> getReadToken()
	{
		return TOKEN;
	}

	@Override
	public CDOMWriteToken<VarContainer> getWriteToken()
	{
		return TOKEN;
	}

	@Override
	protected String getLegalValue()
	{
		return "HP|ADD|roll(\"3d6\")";
	}

	@Override
	protected String getAlternateLegalValue()
	{
		return "HP|ADD|roll(\"1d20\")";
	}

	@Override
	protected ConsolidationRule getConsolidationRule()
	{
		return ConsolidationRule.SEPARATE;
	}

	@Override
	protected void additionalSetup(LoadContext context)
	{
		super.additionalSetup(context);
		FormatManager<?> numberFormat = context.getReferenceContext().getFormatManager("NUMBER");
		PCGenScope scope = context.getActiveScope();
		context.getVariableContext().assertLegalVariableID("HP", scope, numberFormat);
		context.getVariableContext().addFunction(new RollFunction());
	}

	@Test
	void testRoundRobinAddRoll() throws PersistenceLayerException
	{
		runRoundRobin("HP|ADD|roll(\"3d6\")");
	}

	@Test
	void testInvalidUnquotedDice()
	{
		// A bare identifier (not a quoted-string literal) must be rejected
		// at parse time. This is the load-time validation win versus the
		// legacy JEP path, which would have silently returned 0.
		assertFalse(parse("HP|ADD|roll(diceVar)"));
		assertNoSideEffects();
	}
}
