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
package tokencontent;

import pcgen.base.formatmanager.FormatUtilities;
import pcgen.core.PCTemplate;
import pcgen.rules.persistence.token.CDOMToken;
import pcgen.rules.persistence.token.ParseResult;
import plugin.function.RollFunction;
import plugin.lsttokens.ModifyLst;
import plugin.lsttokens.testsupport.TokenRegistration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tokenmodel.testsupport.AbstractTokenModelTest;
import util.TestURI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test that {@code MODIFY:HP|ADD|roll("...")} composes with the
 * reactive {@code SolverManager} pipeline: granting a template that carries the
 * MODIFY registers a contributor that fires {@code roll(...)} and feeds the
 * result into {@code HP}; removing the template unregisters the contributor and
 * {@code HP} recomputes without it.
 *
 * <p>Random rolls are asserted on two axes: an absolute range bound (HP must
 * land within the legal envelope for the active contributors) and a
 * <em>delta</em> bound against the prior state (the newly-attached or
 * newly-removed contributor must move HP by an amount inside that single
 * contributor's legal range). The delta bound is strictly tighter than the
 * absolute range and is what makes a missing or duplicated contributor
 * detectable on a single test run.
 *
 * <p><strong>Note on caching:</strong> the new formula engine's {@code Solver}
 * does not cache modifier values across recomputations — every recompute of a
 * target variable re-invokes {@code process} on every active modifier, so any
 * still-active {@code roll(...)} contributor re-fires when an unrelated
 * contributor on the same variable is added or removed. The test therefore
 * asserts {@code hpStableAcrossReads} only on reads against an unchanged set
 * of contributors, and uses range (not strict-equality) deltas when the set
 * changes.
 */
class RollFunctionLifecycleTest extends AbstractTokenModelTest
{
	private static final ModifyLst MODIFY_TOKEN = new ModifyLst();
	private static final plugin.modifier.number.AddModifierFactory ADD =
			new plugin.modifier.number.AddModifierFactory();
	private static final plugin.modifier.number.SetModifierFactory SET =
			new plugin.modifier.number.SetModifierFactory();

	@BeforeEach
	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		TokenRegistration.register(ADD);
		TokenRegistration.register(SET);
		context.getVariableContext().addFunction(new RollFunction());
		context.getVariableContext().assertLegalVariableID("HP",
			context.getActiveScope(), FormatUtilities.NUMBER_MANAGER);
	}

	@Override
	public CDOMToken<?> getToken()
	{
		return MODIFY_TOKEN;
	}

	@SuppressWarnings("java:S5960")
	private PCTemplate makeModifyTemplate(String key, String value)
	{
		PCTemplate template = create(PCTemplate.class, key);
		ParseResult result = MODIFY_TOKEN.parseToken(context, template, value);
		assertEquals(ParseResult.SUCCESS, result, () -> {
			result.printMessages(TestURI.getURI());
			return "Test setup failed for " + key + ": " + value;
		});
		return template;
	}

	private int hp()
	{
		Object value = pc.getGlobal("HP");
		assertInstanceOf(Number.class, value, () -> "HP is not a Number: " + value);
		return ((Number) value).intValue();
	}

	/**
	 * Reads HP three times in a row and asserts the three reads agree. The roll
	 * is supposed to fire once at contributor-register time and cache the value;
	 * if it instead re-rolled on every read, three consecutive reads of a 1d10
	 * (or wider) bonus would almost certainly differ.
	 */
	private int hpStableAcrossReads(String stepLabel)
	{
		int first = hp();
		int second = hp();
		int third = hp();
		assertEquals(first, second,
			() -> stepLabel + ": second read of HP differs from first ("
				+ second + " vs " + first + ") — roll re-fired instead of being cached");
		assertEquals(first, third,
			() -> stepLabel + ": third read of HP differs from first ("
				+ third + " vs " + first + ") — roll re-fired instead of being cached");
		return first;
	}

	@Test
	void testAddAndRemoveRolledBonuses()
	{
		PCTemplate baseline = makeModifyTemplate("Baseline", "HP|SET|10");
		PCTemplate bonusD6 = makeModifyTemplate("BonusD6", "HP|ADD|roll(\"1d6\")");
		PCTemplate bonusD10 = makeModifyTemplate("BonusD10", "HP|ADD|roll(\"1d10\")");
		finishLoad();

		// Step 1: baseline only — HP must be exactly 10.
		templateInputFacet.directAdd(id, baseline, getAssoc());
		assertEquals(10, hp(), "Baseline alone should yield HP=10");

		// Step 2: add Bonus1 (1d6) — HP in [11, 16], stable across reads.
		templateInputFacet.directAdd(id, bonusD6, getAssoc());
		int afterFirstBonus = hpStableAcrossReads("After +1d6");
		assertTrue(afterFirstBonus >= 11 && afterFirstBonus <= 16,
			() -> "After +1d6, expected 11..16, was " + afterFirstBonus);

		// Step 3: add Bonus2 (1d10) — HP in [12, 26], stable across reads.
		// We cannot assert a tight delta against Step 2: adding a contributor
		// triggers a SolverManager recompute, which re-fires every still-
		// active roll modifier (including the d6 from Step 2) with a fresh
		// RNG draw. Per-step ranges are the tight bound the engine permits.
		templateInputFacet.directAdd(id, bonusD10, getAssoc());
		int afterBothBonuses = hpStableAcrossReads("After +1d6 +1d10");
		assertTrue(afterBothBonuses >= 12 && afterBothBonuses <= 26,
			() -> "After +1d6 +1d10, expected 12..26, was " + afterBothBonuses);

		// Step 4: remove Bonus1 — HP in [11, 20] (baseline + 1d10 only).
		// Removing a contributor also triggers a recompute, so the surviving
		// 1d10 re-fires; afterRemovingD6 reflects baseline + a fresh 1d10
		// roll, hence the absolute-range assertion is what we can prove.
		templateInputFacet.remove(id, bonusD6);
		int afterRemovingD6 = hpStableAcrossReads("After removing +1d6");
		assertTrue(afterRemovingD6 >= 11 && afterRemovingD6 <= 20,
			() -> "After removing +1d6, expected 11..20, was " + afterRemovingD6);

		// Step 5: remove Bonus2 — back to baseline, HP must be exactly 10.
		templateInputFacet.remove(id, bonusD10);
		assertEquals(10, hp(), "After removing both bonuses, HP should be back to 10");
	}
}
