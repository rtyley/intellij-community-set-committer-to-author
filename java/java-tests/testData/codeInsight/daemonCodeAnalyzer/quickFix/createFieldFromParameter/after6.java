// "Create Field for Parameter 'p1'" "true"

class Test{
    int myP1;
    int myP2;
 
    void f(int p1, int p2){
        myP1 = p1;
        int myP2 = p1;
        p1 = 0;
    }
}

