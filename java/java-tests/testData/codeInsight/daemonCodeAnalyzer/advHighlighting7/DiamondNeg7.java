class Neg07 {
   static class SuperFoo<X> {}
   static class Foo<X extends Number> extends SuperFoo<X> {
       Foo(X x) {}
   }

   SuperFoo<String> sf1 = new Foo<><error descr="'Foo(java.lang.String & java.lang.Number)' in 'Neg07.Foo' cannot be applied to '(java.lang.String)'">("")</error>;
}
