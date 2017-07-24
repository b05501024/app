package com.example.android.myapplication;

/**
 * Created by 黃小維 on 2017/7/14.
 */

public class Temperature {

    double temp;

    public void setTemp(int temperature) {

        this.temp = temperature;
    }

    public String getTemp() {

        String tempp = temp/100 + "." + temp%100 ;
        return tempp;
    }


}
