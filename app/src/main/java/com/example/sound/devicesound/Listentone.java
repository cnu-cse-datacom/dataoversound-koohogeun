package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;

import java.lang.reflect.Array;
import java.util.ArrayList;

public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;

    private int findFrequency(int in){
        int i;
        for(i = 1; in >= i; i = i*2);
        if(i - in <= in - i/2)
            return i;
        return i/2;
    }

    public Listentone(){
        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
        mAudioRecord.startRecording();
    }

    private double findFrequency(double[] toTransform) {
        int len = toTransform.length;
        double[] real = new double[len];
        double[] img = new double[len];
        double realNum;
        double imgNum;
        double[] mag = new double[len];


        Complex[] complx = transform.transform(toTransform,TransformType.FORWARD);
        double[] freq = this.fftfreq(complx.length, 1);

        for(int i = 0; i< complx.length; i++){
            realNum = complx[i].getReal();
            imgNum = complx[i].getImaginary();
            mag[i] = Math.sqrt((realNum * realNum) + (imgNum * imgNum));
        }
        double top =0;
        int index = 0;
        for(int i = 0; i < complx.length; i++){
            if(top < mag[i]) {
                top = mag[i];
                index = i;
            }
        }
        return Math.abs(freq[index]);
    }

    private double[] fftfreq(int length, int d){
        double v = (double)1/(length*d);
        double[] res = new double[length];
        for(int i = 0; i < length/2; i++) {
            res[i] = ((double)i) * (mSampleRate*v);
            res[i + length / 2] = ((double)i) * (mSampleRate*v) - mSampleRate / 2;
        }
        return res;
    }

    public void PreRequest() {
        int blocksize = findFrequency((int)(long)Math.round(interval/2*mSampleRate));
        short[] buffer = new short[blocksize];
        double[] buffer_cp = new double[blocksize];
        boolean flag = false;
        ArrayList<Double> raw = new ArrayList<>();
        ArrayList<Integer> cov = new ArrayList<>();
        String res = "";
        char temp;
        while(true){
            int bufferedReadResult = mAudioRecord.read(buffer, 0, blocksize);
            if(bufferedReadResult < 0) {
                continue;
            }

            for(int i = 0; i < blocksize; i ++) {
                buffer_cp[i] = buffer[i];
            }

            double freq = findFrequency(buffer_cp);


            if(flag && match(freq, HANDSHAKE_END_HZ)) {
                cov = extract(raw);
                for(int i = 0 ; i < cov.size() ; i++){
                    temp = (char)(int)cov.get(i);
                    res += Character.toString(temp);
                }
                Log.d("Listentone_result", res);
                raw.clear();
                res = "";
                flag=false;
            }
            else if(flag){
                raw.add(freq);
                Log.d("Listentone", Double.toString(freq));
        }
            else if(match(freq, HANDSHAKE_START_HZ))
                flag =true;
        }

    }

    public boolean match(double freq1, int freq2) {
        return Math.abs(freq1 - (double)freq2) < 20 ;
    }

    public ArrayList<Integer> extract(ArrayList<Double> raw) {
        ArrayList<Integer> con_int = new ArrayList<>();

        for(int i = 0; i < raw.size(); i += 2){
            con_int.add((int)(Math.round((raw.get(i) -START_HZ)/STEP_HZ)));
        }
        for(int i = 1; i < con_int.size(); i++) {
            if (0 >= con_int.get(i) || con_int.get(i) >= 16) {
                con_int.remove(i);
            }
        }
        con_int.remove(0);
        return decode(BITS, con_int);
    }

    public ArrayList<Integer> decode(int chunk_bits, ArrayList<Integer> con_int){
        ArrayList<Integer> out = new ArrayList<>();
        int next_read_chunk = 0 ;
        int next_read_bit = 0 ;
        int byt = 0;
        int bits_left = 8 ;

        while(next_read_chunk < con_int.size()) {
            int can_fill = chunk_bits - next_read_bit;
            int to_fill = Math.min(bits_left, can_fill);
            int offset = chunk_bits - next_read_bit - to_fill;
            byt <<= to_fill;
            int shifted = con_int.get(next_read_chunk) & (((1 << to_fill) - 1) << offset);
            byt |= shifted >> offset;
            bits_left -= to_fill;
            next_read_bit += to_fill;

            if (bits_left <= 0) {
                out.add(byt);
                byt = 0;
                bits_left = 8;
            }

            if (next_read_bit >= chunk_bits){
                next_read_chunk += 1;
                next_read_bit -= chunk_bits;
            }
        }
        return out  ;
    }
}
