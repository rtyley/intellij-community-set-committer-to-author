// "Move initializer to setUp method" "true"
public class X {
  <caret>int i;

    @org.testng.annotations.BeforeMethod
    public void setUp() throws Exception {
        i = 7;
    }

    @org.testng.annotations.Test
  public void test() {
  }
}
