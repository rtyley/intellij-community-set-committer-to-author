class CharSequence {}

class Stringy<S extends CharSequence> { }

class Tester {
void method() { new Stringy<CharSequence><caret> }