package com.gulshansingh.hackerlivewallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class BitSequenceStyleTest {
    private Context context;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().clear().commit();
    }

    @Test
    public void configure_usesDefaults() throws Exception {
        BitSequence.configure(context);

        int defaultNumBits = context.getResources().getInteger(R.integer.default_num_bits);
        int defaultTextSize = context.getResources().getInteger(R.integer.default_text_size);
        int defaultBitColor = context.getColor(R.color.default_bit_color);

        assertEquals(defaultNumBits, getStyleInt("numBits"));
        assertEquals(defaultTextSize, getStyleInt("defaultTextSize"));
        assertEquals(defaultTextSize, getStyleInt("defaultFallingSpeed"));
        assertEquals(defaultBitColor, getStyleInt("color"));
        assertEquals(100, getStyleInt("changeBitSpeed"));
        assertTrue(getStyleBoolean("depthEnabled"));
        assertTrue(getBitSequenceBoolean("isRandom"));
    }

    @Test
    public void configure_customExactText_setsNumBitsToTextLength() throws Exception {
        preferences.edit()
                .putString("character_set_name", "Custom (exact text)")
                .putString("custom_character_string", "HELLO")
                .apply();

        BitSequence.configure(context);

        assertFalse(getBitSequenceBoolean("isRandom"));
        assertEquals(5, getStyleInt("numBits"));
    }

    @Test(expected = RuntimeException.class)
    public void configure_customRandomEmpty_throws() {
        preferences.edit()
                .putString("character_set_name", "Custom (random characters)")
                .putString("custom_character_set", "")
                .apply();

        BitSequence.configure(context);
    }

    @Test
    public void configure_readsBitColorFromPreferences() throws Exception {
        int customColor = 0xFF123456;
        preferences.edit()
                .putInt(SettingsActivity.KEY_BIT_COLOR, customColor)
                .apply();

        BitSequence.configure(context);

        assertEquals(customColor, getStyleInt("color"));
    }

    private int getStyleInt(String fieldName) throws Exception {
        Field field = BitSequence.Style.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private boolean getStyleBoolean(String fieldName) throws Exception {
        Field field = BitSequence.Style.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(null);
    }

    private boolean getBitSequenceBoolean(String fieldName) throws Exception {
        Field field = BitSequence.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(null);
    }
}
