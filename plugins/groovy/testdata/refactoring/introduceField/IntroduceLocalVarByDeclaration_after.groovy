class Foo {
    def bar = 3
    def f = 4 - bar

    def foo() {
        print "a"

        <selection></selection>print f
        f++
        f -=4
        print f
    }
}