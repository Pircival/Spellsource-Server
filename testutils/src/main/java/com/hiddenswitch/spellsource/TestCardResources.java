package com.hiddenswitch.spellsource;

public class TestCardResources extends AbstractCardResources<TestCardResources> {

	public TestCardResources() {
		super(TestCardResources.class);
	}

	@Override
	public String getDirectoryPrefix() {
		return "test";
	}
}
