// "<html> Change signature of foo(<b>ArrayList&lt;Integer&gt;</b>, <b>ArrayList&lt;Integer&gt;</b>, ArrayList&lt;Integer&gt;, ArrayList&lt;Integer&gt;)</html>" "true"
import java.util.*;

class Test {

    public void foo(ArrayList<Integer> l, ArrayList<Integer> l1) {}

    {
      foo(new <caret>ArrayList<Integer>(), new ArrayList<Integer>(), new ArrayList<Integer>(), new ArrayList<Integer>());
    }
}