public class TestCase extends Zzz {

    Object ooo() {}

    {
        if (!(ooo() instanceof String)) {
        } else {
          String s = o<caret>
        }
    }
}