// "Replace with lambda" "true"
class Test {
  {
    Comparable<String> c = new Compa<caret>rable<String>() {
      @Override
      public int compareTo(String o) {
        return 0;
      }
    }; 
  }
}