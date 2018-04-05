package ris58h.lacopom;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class PomFoldingTest extends LightCodeInsightFixtureTestCase {

    @Override
    protected String getTestDataPath() {
        return "testData";
    }

    public void testFolding() {
        myFixture.testFolding(getTestDataPath() + "/pom.xml");
    }
}
