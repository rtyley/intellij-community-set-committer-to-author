// "Access static 'AClass.getA()' via class 'AClass' reference" "true"

class AClass
{
    static AClass getA() {
        return null;
    }
    static int fff;
}

class acc {
 int f() {
   AClass a = null;
   <caret>a.getA();
   return 0;
 }
}