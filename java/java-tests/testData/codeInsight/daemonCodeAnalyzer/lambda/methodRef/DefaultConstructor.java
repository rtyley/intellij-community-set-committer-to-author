class DefaultConstructor {
    
    interface I1<R> {
        R invoke();
    }

    interface I2<R, A> {
        R invoke(A a);
    }
    
    static class Outer {
        class Inner {
        }
        
        static void test1() {
            I2<Inner, Outer> i2 = Inner :: new;
            <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,java.lang.String>'">I2<Inner, String> i2str = Inner :: new;</error>
        }
        
        void test2() {
            I1<Inner> i1 = Inner :: new;
            <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I1<java.lang.Integer>'">I1<Integer> i1Int = Inner :: new;</error>
            I2<Inner, Outer> i2 =  Inner :: new;
        }
    }
    
    static void test1() {
        I2<Outer.Inner, Outer> i2 = Outer.Inner::new;
        <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,java.lang.String>'">I2<Outer.Inner, String> i2str = Outer.Inner::new;</error>
    }
    
    void test2() {
        I2<Outer.Inner, Outer> i2 = Outer.Inner::new;
        <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,java.lang.String>'">I2<Outer.Inner, String> i2str = Outer.Inner::new;</error>
    }
}

class DefaultConstructor1 {

    public void bar() {
    }

    {
        Runnable b1 = DefaultConstructor1 :: new;
    }
}

class DefaultConstructor2 {
    interface I {
        void foo(DefaultConstructor2 e);
    }


    void f() {
        <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor2.I'">I i1 = DefaultConstructor2 :: new;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor2.I'">I i2 = this::new;</error>
    }
}

class DefaultConstructor3 {
   public class Inner {}
   public static class StaticInner {}
   
   static <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor3.I'">I i = Inner::new;</error>
   static I1 i1 = StaticInner::new;
   interface I {
     Inner foo();
   }

   interface I1 {
     StaticInner foo();
   }
}

class DefaultConstructor4 {
   public class Inner {}
   public static class StaticInner {}
   
   static I i = Inner::new;
   static <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor4.I1'">I1 i1 = StaticInner::new;</error>
   interface I {
     Inner foo(DefaultConstructor4 receiver);
   }

   interface I1 {
     StaticInner foo(DefaultConstructor4 receiver);
   }
}
