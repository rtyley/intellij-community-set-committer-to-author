package org.jetbrains.ether.dependencyView;

import java.io.PrintStream;

/**
 * Created with IntelliJ IDEA.
 * User: db
 * Date: 24.04.12
 * Time: 0:44
 * To change this template use File | Settings | File Templates.
 */

interface Streamable {
  void toStream(DependencyContext context, PrintStream stream);
}
