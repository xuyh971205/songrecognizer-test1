package com.example.songrecognizer_test1;

public class Complex {
    public double real, imag;
    public Complex(double real, double im){
        this.real = real;
        this.imag = im;
    }

    public Complex(){
        this(0,0);
    }

    public Complex(Complex c){
        this(c.real,c.imag);
    }

    @Override
    public String toString() {
        return "("+this.real+"+"+this.imag +"i)";
    }

    // add
    public final Complex add(Complex c){
        return new Complex(this.real+c.real,this.imag +c.imag);
    }

    // minus
    public final Complex minus(Complex c){
        return new Complex(this.real-c.real,this.imag -c.imag);
    }

    // return abs/modulus/magnitude and angle/phase/argument
    public double abs() {
        return Math.hypot(real, imag);
    } // Math.sqrt(re*re + im*im)

    // get mod
    public final double getMod(){
        return Math.sqrt(this.real * this.real+this.imag * this.imag);
    }

    // multiply/times
    public final Complex multiply(Complex c){
        return new Complex(
                this.real*c.real - this.imag *c.imag,
                this.real*c.imag + this.imag *c.real);
    }
}