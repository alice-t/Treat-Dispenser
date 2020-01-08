package com.example.smartdog.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;

import com.example.smartdog.R;

import java.util.ArrayList;

public class SoundManager
{
    private static final String TAG = SoundManager.class.getName();
    private static SoundPool soundpool;
    private ArrayList<String> sounds;
    private static boolean allLoaded = false;
    private static SoundManager instance = null;

    private SoundManager(Context context)
    {
        if (soundpool == null)
        {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP)
            {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                soundpool = new SoundPool.Builder()
                        .setMaxStreams(6)
                        .setAudioAttributes(audioAttributes)
                        .build();
            }
            else
            {
                soundpool = new SoundPool(7, AudioManager.STREAM_MUSIC, 0);
            }
            soundpool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
                if (status == 0 && sampleId == 7) {
                    allLoaded = true;
                }
            });
            soundpool.load(context, R.raw.bloop, 1);
            soundpool.load(context, R.raw.ding, 1);
            soundpool.load(context, R.raw.electro_chirp, 1);
            soundpool.load(context, R.raw.bark, 1);
            soundpool.load(context, R.raw.oi, 1);
            soundpool.load(context, R.raw.trill, 1);
            soundpool.load(context, R.raw.nuh_uh, 1);
            sounds = new ArrayList<String>();
            sounds.add("bloop");
            sounds.add("reception bell");
            sounds.add("electro chirp");
            sounds.add("dog bark");
            sounds.add("Oi!");
            sounds.add("trill");
            sounds.add("uh-oh");
        }
    }

    public static SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context);
        }
        return instance;
    }

    public void playSample(String sound)
    {
        int count = 0; // wait no more than 5 seconds

        while(!allLoaded && count++ < 5)
        {
            try
            {
                Thread.sleep(1000);
            }
            catch ( Exception e)
            {
                Log.d (TAG, e.toString());
            }
        }
        int soundId = sounds.indexOf(sound);
        if (soundId != -1)
            soundpool.play(soundId + 1, 1, 1, 0, 0, 1);
    }

    public static void cleanUp()
    {
        if (soundpool != null)
        {
            soundpool.release();
            soundpool = null;
            instance = null;
        }

    }

}
