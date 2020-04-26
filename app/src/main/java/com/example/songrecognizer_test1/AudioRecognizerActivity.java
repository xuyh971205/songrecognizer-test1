package com.example.songrecognizer_test1;

import androidx.appcompat.app.AppCompatActivity;
import androidx.arch.core.executor.DefaultTaskExecutor;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AudioRecognizerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_recognizer);
        FloatingActionButton button = findViewById(R.id.floatingActionButton);
    }


    private static final String FILE_NAME = "MainMicRecord";
    private static final int SAMPLE_RATE = 44100;   //Hz, sampling rate: 44100 times of sampling per minute.
//    private static final double FREQUENCY = 500;    //Hz, standard rate. analyzing 500hz here.
    private static final double RESOLUTION = 10;    //Hz, deviation
    private static final long RECORD_TIME = 2000;
    private File mSampleFile;
    private int bufferSize = 0; // initialize an int bufferSize.
    private AudioRecord mAudioRecord;
    public static final int FFT_N = 4096;

    // Map<>: store corresponding serial number and the keys, one to one.
    //	Since the the audio can be long,
    //	the serial number should be stored in a Long-int.
    Map<Long, List<DataPoint>> hashMap;
    Map<Integer, Map<Integer, Integer>> matchMap; 	// Map<SongId, Map<Offset, Count>>




    // 4.22: Because there's a release() at the end of the thread,
    // so I can interpret like, it will record for a preset time and stop automatically,
    // and I Don't have to press the button to stop and release() by myself.
    private void startRecord() {
        try {
            mSampleFile = new File(getFilesDir()+"/"+FILE_NAME); // "MainMicRecord"
            if(mSampleFile.exists()){
                if(!mSampleFile.delete()){
                    return;
                }
            }
            if(!mSampleFile.createNewFile()){
                return;
            }
        } catch(IOException e) {
            return;
        }

        // For convenience, only record mono (sound from single track) here.

        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        mAudioRecord.startRecording();

        new Thread(new AudioRecordThread()).start();
    }


    // thread: writing recorded audio data into a file.
    class AudioRecordThread implements Runnable{
        @Override
        public void run() {
            short[] audiodata = new short[bufferSize/2];
            DataOutputStream fos = null;

            try {
                // Set mSampleFile as the output destination of fos.
                fos = new DataOutputStream( new FileOutputStream(mSampleFile));

                int readSize;

                // If it's recording...
                while (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING){
                    // Read short-integer data from the record buffer to the variable readSize.
                    readSize = mAudioRecord.read(audiodata,0,audiodata.length);

                    // If readSize wan't uninitialized, which is being initialized...
                    if(AudioRecord.ERROR_INVALID_OPERATION != readSize){
                        // One by one, write the audio data from the short array to the fos object.
                        for(int i = 0; i < readSize; i++){
                            fos.writeShort(audiodata[i]);
                            fos.flush(); // flush(): flush the stream, to force the data be sent into the destination.
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                if(fos != null){
                    try {
                        fos.close(); // Close the writing stream.
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // Release here.
                mAudioRecord.release();
                mAudioRecord = null;
            }
        }
    };


    // Stop recording, but don't release() here.
    // It seems like pausing but not stopping.
    private void stopRecording() {
        mAudioRecord.stop();
    }


    //
    private void frequencyAnalyse(){

        if(mSampleFile == null){
            return;
        }

        // Input the data out from the mSampleFile.
        DataInputStream inputStream = null;
        try {
            inputStream = new DataInputStream(new FileInputStream(mSampleFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // get the total length of input stream.
        int totalLength = 0;
        try {
            totalLength = inputStream.available();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Storing all the short-type data from the record.
        short[] buffer = new short[totalLength];
        // Input data from the file by the object inputStream, to store in buffer[], one short by one short.
        for(int i = 0; i < totalLength; i++){
            try {
                buffer[i] = inputStream.readShort();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } // Now there's all the data in buffer.

        int frameAmount = totalLength / 4096;

        // When turning into frequency domain, we'll need complex numbers:
        Complex[][] results = new Complex[frameAmount][];

        // Transform all the frames into frequency domain, one frame by one frame:
        for (int frame = 0; frame < frameAmount; frame++) {
            Complex[] complex = new Complex[4096];
            for (int i = 0; i < 4096; i++) {
                // Put the time domain data into a complex number with imaginary part as 0:
                complex[i] = new Complex(buffer[(frame * 4096) + i], 0);
            }
            // Perform FFT analysis on the frame:
            results[frame] = FFT.fft(complex);
        }
        // Now all the frames are transformed into frequency domain.

        determinKeyPoints(results);
    }






    static double[][] highscores;
    static double[][] recordPoints;
    static long[][] points;

    public static int UPPER_LIMIT = 300;
    public static int LOWER_LIMIT = 40;

    public static final int[] RANGE = new int[] { 40, 80, 120, 180, UPPER_LIMIT + 1 };

    // Find out which range the freq is in.
    public static int getIndex(int freq) {
        int i = 0;
        while (RANGE[i] < freq)
            i++;
        return i;
    }


    //
    public double determinKeyPoints(Complex[][] results){
        this.matchMap = new HashMap<Integer, Map<Integer, Integer>>();

        /**----NEED TO BE MODIFIED----------

         FileWriter fstream = null;
        try {
            fstream = new FileWriter("result.txt");
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        BufferedWriter outFile = new BufferedWriter(fstream);

         */

        // Initialize the two-dimentional arrays with all 0.
        // results.length = frameAmount
        highscores = new double[results.length][5];
        for (int i = 0; i < results.length; i++) {
            for (int j = 0; j < 5; j++) {
                highscores[i][j] = 0;
            }
        }
        recordPoints = new double[results.length][UPPER_LIMIT];
        for (int i = 0; i < results.length; i++) {
            for (int j = 0; j < UPPER_LIMIT; j++) {
                recordPoints[i][j] = 0;
            }
        }
        points = new long[results.length][5];
        for (int i = 0; i < results.length; i++) {
            for (int j = 0; j < 5; j++) {
                points[i][j] = 0;
            }
        }


        // For every frame,
        for (int frame = 0; frame < results.length; frame++) {
            // For every freq (frequency) between 40 and 300, get its magnitude, find out wich range it's in.
            // and store it in the three arrays.
            for (int freq = LOWER_LIMIT; freq < UPPER_LIMIT - 1; freq++) {

                // Get the magnitude of this frequency-----------------------
                double mag = Math.log(results[frame][freq].abs() + 1);

                //Find out which range the freq is in--------------------------
                int index = getIndex(freq);

                 // Save the magnitude and corresponding frequency---------------
                if (mag > highscores[frame][index]) {
                    // The highest magnitude in every range in every frame
                    highscores[frame][index] = mag;
                    // The array was initially all 0.
                    // The frequency with the highest magnitude will be marked 1
                    recordPoints[frame][freq] = 1;
                    // Store the actual number of frequency
                    points[frame][index] = freq;
                }
            }

            /**----NEED TO BE MODIFIED----------

             // Write the points to a file:
             try {
             for (int k = 0; k < 5; k++) { // 0, 1, 2, 3, 4
             outFile.write("" + highscores[t][k] + ";"
             + recordPoints[t][k] + "\t");
             }
             outFile.write("\n");
             } catch (IOException e) {
             e.printStackTrace();
             }

             */

            // One hash key, h.
            // According to the author's article, he chose the front 4 frequencys to build a hash point.

            // Pass the actual number of frequency in each range of current frame,
            //  to build and get a hash key returned.
            long hashKey = hash(points[frame][0], points[frame][1], points[frame][2], points[frame][3]);


            //Start matching.
            if (isMatching) {
                List<DataPoint> listPoints; // A list of DataPoint, named listPoints.
                if ((listPoints = hashMap.get(hashKey)) != null) {
                    // For each DataPoint-type dp in the list listPoints,
                    for (DataPoint dP : listPoints) {
                        //
                        int offset = Math.abs(dP.getTime() - t);

                        // Initialize a map tmpMap.
                        Map<Integer, Integer> tmpMap = null;

                        // Check whether there's already been a song being Hashed in the database.

                        // Get the relative map<int, int> of SongId in the matchMap.
                        // If got null, which means map<int, int> doesn't exist,
                        // 	it means this song haven't been stored in the match map.
                        if ((tmpMap = this.matchMap.get(dP.getSongId())) == null) {
                            // Make tmpMap a hash map, with two ints.
                            tmpMap = new HashMap<Integer, Integer>();
                            // Put the int value of offset and an int 1, into the hash map tmpMap.
                            tmpMap.put(offset, 1);
                            // Add this song into the match map.
                            matchMap.put(dP.getSongId(), tmpMap);
                        }
                        //
                        else {
                            Integer count = tmpMap.get(offset);
                            if (count == null) {
                                tmpMap.put(offset, new Integer(1));
                            } else {
                                tmpMap.put(offset, new Integer(count + 1));
                            }
                        }
                    }
                }
            }




        }




    }


    private static final int FUZ_FACTOR = 2;

    // Define function hash.
    private long hash(long p1, long p2, long p3, long p4) {
        return (p4 - (p4 % FUZ_FACTOR)) * 100000000 + (p3 - (p3 % FUZ_FACTOR))
                * 100000 + (p2 - (p2 % FUZ_FACTOR)) * 100
                + (p1 - (p1 % FUZ_FACTOR));
    }









}
