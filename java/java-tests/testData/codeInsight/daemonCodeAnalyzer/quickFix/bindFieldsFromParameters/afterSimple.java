// "Bind Constructor Parameters to Fields" "true"

class Bar {

  private int myi1;
  private int myi2;
    private final int myI1;
    private final int myI2;
    private final String myI3;

    Bar(int i1, int i2, String i3) {
        myI1 = i1;
        myI2 = i2;
        myI3 = i3;
    }
}