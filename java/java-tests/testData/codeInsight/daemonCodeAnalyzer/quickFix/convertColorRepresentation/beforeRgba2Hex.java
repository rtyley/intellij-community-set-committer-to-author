// "Convert to 'new Color(0x2564788c,true)'" "true"

package java.awt;

class A {
  private Color color = new Color(37, 100,<caret> 120, 140);
}

class Color {
  Color(int r, int g, int b) {
  }

  Color(int r, int g, int b, int a) {
  }

  Color(int rgb) {
  }

  Color(int rgba, boolean hasAlpha) {
  }
}
