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
 * along with this library; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */
package tokencontent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import pcgen.cdom.content.DatasetVariable;
import pcgen.core.PCTemplate;
import pcgen.rules.persistence.token.CDOMToken;
import pcgen.rules.persistence.token.ParseResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import plugin.lsttokens.InfoLst;
import plugin.lsttokens.InfoVarsLst;
import plugin.lsttokens.testsupport.TokenRegistration;
import plugin.lsttokens.variable.GlobalToken;
import tokenmodel.testsupport.AbstractTokenModelTest;
import util.TestURI;

/**
 * Integration test that INFOVARS resolves a GLOBAL variable end-to-end: a
 * variable declared with the real GLOBAL token is accepted by INFOVARS, while a
 * variable that was never declared globally is rejected. This exercises the
 * "INFOVARS can only reference global variables" rule against the actual formula
 * system (not a programmatically-asserted variable).
 */
class GlobalInfoVarsTest extends AbstractTokenModelTest
{

	private static final GlobalToken GLOBAL_TOKEN = new GlobalToken();
	private static final InfoLst INFO_TOKEN = new InfoLst();
	private static final InfoVarsLst INFOVARS_TOKEN = new InfoVarsLst();

	@BeforeEach
	@Override
	public void setUp() throws Exception
	{
		super.setUp();
		TokenRegistration.register(INFO_TOKEN);
	}

	@Override
	public CDOMToken<?> getToken()
	{
		// The token under test; AbstractTokenModelTest registers this automatically.
		return INFOVARS_TOKEN;
	}

	/**
	 * Declares a global NUMBER variable of the given name via the real GLOBAL token.
	 */
	private void declareGlobalVar(String varName)
	{
		DatasetVariable dv = new DatasetVariable();
		ParseResult result = GLOBAL_TOKEN.parseToken(context, dv, "NUMBER=" + varName);
		if (!result.passed())
		{
			result.printMessages(TestURI.getURI());
			throw new IllegalStateException("Setup failed: could not declare global var " + varName);
		}
		context.getReferenceContext().importObject(dv);
	}

	@Test
	void testInfoVarsResolvesGlobalVariable()
	{
		declareGlobalVar("InfoTestVar");
		PCTemplate template = create(PCTemplate.class, "TemplateWithInfo");

		// INFO with one placeholder, INFOVARS supplying a declared global variable.
		assertTrue(INFO_TOKEN.parseToken(context, template, "Detail|Value is {0}.").passed());
		assertTrue(INFOVARS_TOKEN.parseToken(context, template, "Detail|InfoTestVar").passed());

		// Full deferred resolution (runs InfoLst.process, resolveReferences, etc.).
		finishLoad();
	}

	@Test
	void testInfoVarsRejectsUndeclaredVariable()
	{
		// No global variable declared: INFOVARS must reject the reference at parse time.
		PCTemplate template = create(PCTemplate.class, "TemplateWithBadInfo");
		assertTrue(INFO_TOKEN.parseToken(context, template, "Detail|Value is {0}.").passed());
		assertFalse(INFOVARS_TOKEN.parseToken(context, template, "Detail|NotDeclaredGlobally").passed(),
				"INFOVARS must reject a variable that is not a declared global");
	}

	/**
	 * Documents a divergence from the 2018 INFO/INFOVARS spec: the spec describes a
	 * scoped form INFOVARS:x|SCOPE=var (with the scope prefix optional for the
	 * default VAR scope). That grammar is NOT implemented — INFOVARS currently
	 * accepts only bare global variable names, so a "SCOPE=var" token is treated as
	 * a single (illegal) variable name and rejected. This test pins that boundary;
	 * if the scoped form is ever implemented, it should be updated to expect success.
	 */
	@Test
	void testInfoVarsScopedFormNotYetSupported()
	{
		declareGlobalVar("InfoTestVar");
		PCTemplate template = create(PCTemplate.class, "TemplateScopedForm");
		assertTrue(INFO_TOKEN.parseToken(context, template, "Detail|Value is {0}.").passed());
		assertFalse(INFOVARS_TOKEN.parseToken(context, template, "Detail|VAR=InfoTestVar").passed(),
				"The spec's scope=variable form (e.g. VAR=x) is not implemented; it must currently be rejected");
	}
}
