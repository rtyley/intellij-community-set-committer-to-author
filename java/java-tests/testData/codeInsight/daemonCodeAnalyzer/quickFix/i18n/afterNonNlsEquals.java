import org.jetbrains.annotations.NonNls;

// "Annotate variable 'a' as @NonNls" "true"
class Foo {
  public boolean doTest() {
    @NonNls String a;
    return "test".equals(a)
  }
}
