package com.example.alan.bikelog;

/**
 * Created by Alan on 14/09/2015.
 */
public class BikeData {

    private double mVolts = 0;
    private double mCurrent = 0;
    private double mTemp1 = 0;
    private double mTemp2 = 0;

    private long    mStartTime = 0;

    public boolean setData(byte [] data){
        boolean bRet = false;
        if(data[0] == (byte)0xAA) {
            if (data.length != 10) {
                bRet = false;
            } else {
                int val = (data[2] & 0xff) + (data[3] & 0xff) * 256;
                mCurrent = Current(val);
                val = data[4] & 0xff;
                val += (data[5] & 0xff) * 256;
                mVolts = Volts(val);
                val = (data[6] & 0xff) + (data[7] & 0xff) * 256;
                mTemp1 = Temp(val);
                val = (data[8] & 0xff) + (data[9] & 0xff) * 256;
                mTemp2 = Temp(val);
                bRet = true;
            }
        }
        else
            bRet = false;
        return bRet;
    }

    public void setStartTime(long startTime) {
        this.mStartTime = startTime;
        //TODO reset state/accumulations etc
    }

    public double getVolts() {
        return mVolts;
    }
    public String getVoltsTxt() { return String.format("%.1f",mVolts);}

    public double getCurrent() {
        return mCurrent;
    }
    public String getCurrentTxt() { return String.format("%.1f",mCurrent);}

    public double getTemp1() {
        return mTemp1;
    }
    public String getTemp1Txt() { return String.format("%.1f",mTemp1);}

    private double Volts(int ADC){
        double dRet = (double)(ADC);
        dRet = dRet * 0.011856 +0.003394;
        return dRet;
    }

    private double Current(int ADC){
        double dRet = (double)ADC * -0.0126781 + 29.2785;
        return dRet;
    }

    private double Temp(int ADC){
        double dRet = (double)ADC / 2048;
        return (dRet -0.5) * 100;
    }

}
