// "Add Exception to Method Signature" "true"
import java.io.*;

class C {
  String detectEncoding(File inputFile) throws FileNotFoundException, ObjectStreamException {
    final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), "UTF-8"));
    final String line = reader.<caret>readLine();

    return "ISO-8859-1";
  }
}
