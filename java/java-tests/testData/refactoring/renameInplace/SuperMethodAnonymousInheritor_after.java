class Demo {
    class MyEvent<T> {}
    interface MyEventListener<T> {
        void xxx(MyEvent<T> event);
    }

    class Driver {
        void method() {
            MyEventListener<Object> l = new MyEventListener<Object>() {
                public void xxx(MyEvent<Object> event) {
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            };
        }
    }
}