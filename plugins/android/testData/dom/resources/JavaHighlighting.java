package p1.p2;

public class JavaCompletion1 {
  public void f() {
    int n1 = R.string.my_string;
    int n2 = R.string.<error>unknown</error>;
    <error>int n3 = R.styleable.MyStyleable;</error>
    int[] n4 = R.styleable.MyStyleable;
    int n5 = R.attr.myAttr1;
    n5 = R.attr.myAttr2;
    n5 = R.attr.<error>android_text</error>;
  }
}