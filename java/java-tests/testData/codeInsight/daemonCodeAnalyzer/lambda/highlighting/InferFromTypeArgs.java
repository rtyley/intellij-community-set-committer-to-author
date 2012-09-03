import java.util.*;
public class BugReportLambdaSquiggles<T> {

  private T t;
  public <V> List<V> flatMap(Mapper<T, List<V>> mapper) {
    return mapper.map(t);
  }

  static void bar( BugReportLambdaSquiggles<Integer> x) {
    x.flatMap(t1 -> new ArrayList<String>(t1));
  }

  interface Mapper<T, U> {
    U map(T t);
  }
}
