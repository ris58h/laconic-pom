package ris58h.lacopom;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class PomFoldingTest extends LightCodeInsightFixtureTestCase {

    @Override
    protected String getTestDataPath() {
        return "testData";
    }

    public void testParent() {
        doNamedTest();
    }

    public void testDependencyManagement() {
        doNamedTest();
    }

    public void testDependencies() {
        doNamedTest();
    }

    private void doNamedTest() {
        myFixture.testFolding(getTestDataPath() + "/" + getTestName(true) + ".xml");
    }
}
