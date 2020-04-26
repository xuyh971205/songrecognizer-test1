package com.example.songrecognizer_test1;

import android.util.Log;

// FFT analyze: get the highest frequency.
public class FFT {

    // Fast Fourier Transform algorithm, the same as the open source code from Princeton.
    public static Complex[] fft(Complex[] data){
        int N = data.length;
        if(N==1){
            return new Complex[]{data[0]};
        }
        if(N%2 != 0){
            throw new RuntimeException("N is not a power of 2");
        }

        //fft of even/odd terms
        Complex[] even = new Complex[N/2];
        Complex[] odd = new Complex[N/2];
        for(int k = 0;k<N/2;k++){
            even[k] = data[2*k];
            odd[k] = data[2*k+1];
        }
        Complex[] q = fft(even);
        Complex[] r = fft(odd);

        Complex[] y = new Complex[N];
        for (int k = 0;k<N/2;k++){
            double kth = -2*k*Math.PI/N;
            Complex wk = new Complex(Math.cos(kth), Math.sin(kth));
            y[k] = q[k].add(wk.multiply(r[k]));
            y[k+N/2] = q[k].minus(wk.multiply(r[k]));
        }

        return y;
    }
}