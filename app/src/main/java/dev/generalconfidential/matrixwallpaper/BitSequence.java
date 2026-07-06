package dev.generalconfidential.matrixwallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.BlurMaskFilter;
import android.graphics.BlurMaskFilter.Blur;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.preference.PreferenceManager;

import dev.generalconfidential.matrixwallpaper.settings.CharacterSetPreference;
import dev.generalconfidential.matrixwallpaper.thirdparty.ArrayDeque;

import java.util.Random;

import static dev.generalconfidential.matrixwallpaper.SettingsActivity.KEY_BIT_COLOR;
import static dev.generalconfidential.matrixwallpaper.SettingsActivity.KEY_CHANGE_BIT_SPEED;
import static dev.generalconfidential.matrixwallpaper.SettingsActivity.KEY_ENABLE_DEPTH;
import static dev.generalconfidential.matrixwallpaper.SettingsActivity.KEY_FALLING_SPEED;
import static dev.generalconfidential.matrixwallpaper.SettingsActivity.KEY_NUM_BITS;
import static dev.generalconfidential.matrixwallpaper.SettingsActivity.KEY_TEXT_SIZE;

/**
 * A class that stores a list of bits. The first bit is removed and a new bit
 * is appended at a fixed interval. Calling the draw method displays the bit
 * sequence vertically on the screen. Every time a bit is changed, the position
 * of the sequence on the screen is shifted downward. Moving past the bottom of
 * the screen causes the sequence to be placed above the screen.
 *
 * All state is owned by the render thread: {@link #update(long)} advances the
 * animation based on elapsed time and {@link #draw(Canvas)} paints it. There
 * are no background threads.
 *
 * @author Gulshan Singh
 */
public class BitSequence {

	/** The Mask to use for blurred text */
	private static final BlurMaskFilter blurFilter = new BlurMaskFilter(3,
			Blur.NORMAL);

	/** The Mask to use for slightly blurred text */
	private static final BlurMaskFilter slightBlurFilter = new BlurMaskFilter(
			2, Blur.NORMAL);

	/** The Mask to use for regular text */
	private static final BlurMaskFilter regularFilter = null;

	/** The maximum random delay before a sequence starts falling, in ms */
	private static final int MAX_START_DELAY = 6000;

	/** The height of the screen */
	private static int HEIGHT;

	/** Reused for measuring text width, to avoid per-call allocations */
	private static final Paint measurePaint = new Paint();

	/** The bits this sequence stores */
	private final ArrayDeque<String> bits = new ArrayDeque<>();

	/** A variable used for all operations needing random numbers */
	private final Random r = new Random();

	/** The position to draw the sequence at on the screen */
	float x, y;

	/** The uptime at which the next bit change should occur, in ms */
	private long nextChangeTime;

	/** The characters to use in the sequence */
	private static String[] symbols = null;

	/** Describes the style of the sequence */
	private final Style style = new Style();
	private static String charSet;
	private static boolean isRandom = true;
	private int curChar = 0;

	public static class Style {
		/** The default speed at which bits should be changed */
		private static final int DEFAULT_CHANGE_BIT_SPEED = 100;

		/** The maximum alpha a bit can have */
		private static final int MAX_ALPHA = 240;

		private static int changeBitSpeed;
		private static int numBits;
		private static int color;
		private static int defaultTextSize;
		private static int defaultFallingSpeed;
		private static boolean depthEnabled;

		private static int alphaIncrement;
		private static int initialY;

		private int textSize;
		private int fallingSpeed;
		private BlurMaskFilter maskFilter;

		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

		public static void initParameters(Context context) {
			SharedPreferences sp = PreferenceManager
					.getDefaultSharedPreferences(context);
			String charSetName = sp.getString("character_set_name", "Binary");
			isRandom = true;
			if (charSetName.equals("Binary")) {
				charSet = CharacterSetPreference.BINARY_CHAR_SET;
			} else if (charSetName.equals("Matrix")) {
				charSet = CharacterSetPreference.MATRIX_CHAR_SET;
			} else if (charSetName.equals("Custom (random characters)")) {
				charSet = sp.getString("custom_character_set", "");
				if (charSet.length() == 0) {
					throw new RuntimeException("Character set length can't be 0");
				}
			} else if (charSetName.equals("Custom (exact text)")) {
				isRandom = false;
				charSet = sp.getString("custom_character_string", "");
				if (charSet.length() == 0) {
					throw new RuntimeException("Character set length can't be 0");
				}
			} else {
				if (!charSetName.equals("Custom")) { // Legacy character set
					throw new RuntimeException("Invalid character set " + charSetName);
				} else {
					sp.edit().putString("character_set_name", "Custom (random characters)")
							.commit();
					charSet = sp.getString("custom_character_set", "");
					if (charSet.length() == 0) {
						throw new RuntimeException("Character set length can't be 0");
					}
				}
			}
			symbols = charSet.split("(?!^)");

			PreferenceUtility preferences = new PreferenceUtility(context);

			if (isRandom) {
				numBits = preferences.getInt(KEY_NUM_BITS,
						R.integer.default_num_bits);
			} else {
				numBits = charSet.length();
			}
			color = preferences
					.getInt(KEY_BIT_COLOR, R.color.default_bit_color);

			defaultTextSize = preferences.getInt(KEY_TEXT_SIZE,
					R.integer.default_text_size);

			double changeBitSpeedMultiplier = 100 / preferences.getDouble(
					KEY_CHANGE_BIT_SPEED, R.integer.default_change_bit_speed);
			double fallingSpeedMultiplier = preferences.getDouble(
					KEY_FALLING_SPEED, R.integer.default_falling_speed) / 100;

			changeBitSpeed = (int) (DEFAULT_CHANGE_BIT_SPEED * changeBitSpeedMultiplier);
			defaultFallingSpeed = (int) (defaultTextSize * fallingSpeedMultiplier);

			depthEnabled = preferences.getBoolean(KEY_ENABLE_DEPTH, true);

			alphaIncrement = MAX_ALPHA / numBits;
			initialY = -1 * defaultTextSize * numBits;
		}

		public Style() {
			paint.setColor(color);
		}

		public void createPaint() {
			paint.setTextSize(textSize);
			paint.setMaskFilter(maskFilter);
		}

		private static class PreferenceUtility {
			private final SharedPreferences preferences;
			private final Resources res;

			public PreferenceUtility(Context context) {
				preferences = PreferenceManager
						.getDefaultSharedPreferences(context);
				res = context.getResources();
			}

			public int getInt(String key, int defaultId) {
				return preferences.getInt(key, res.getInteger(defaultId));
			}

			public double getDouble(String key, int defaultId) {
				return (double) preferences.getInt(key,
						res.getInteger(defaultId));
			}

			public boolean getBoolean(String key, boolean defaultVal) {
				return preferences.getBoolean(key, defaultVal);
			}
		}
	}

	/**
	 * Resets the sequence by repositioning it, resetting its visual
	 * characteristics, and scheduling its next bit change with a random delay
	 *
	 * @param now
	 *            the current uptime in milliseconds
	 */
	private void reset(long now) {
		y = Style.initialY;
		setDepth();
		style.createPaint();
		nextChangeTime = now + r.nextInt(MAX_START_DELAY);
	}

	/**
	 * Advances the animation to the given time, changing bits and moving the
	 * sequence downward as needed. Must be called from the render thread.
	 *
	 * @param now
	 *            the current uptime in milliseconds
	 */
	public void update(long now) {
		while (now >= nextChangeTime) {
			changeBit();
			y += style.fallingSpeed;
			if (y > HEIGHT) {
				reset(now);
				return;
			}
			nextChangeTime += Style.changeBitSpeed;
		}
	}

	/**
	 * Realigns the sequence's schedule after the wallpaper was invisible, so
	 * that it doesn't try to catch up on all the time that passed
	 *
	 * @param now
	 *            the current uptime in milliseconds
	 */
	public void resume(long now) {
		if (nextChangeTime < now) {
			nextChangeTime = now + r.nextInt(Style.changeBitSpeed + 1);
		}
	}

	private void setDepth() {
		if (!Style.depthEnabled) {
			style.textSize = Style.defaultTextSize;
			style.fallingSpeed = Style.defaultFallingSpeed;
		} else {
			double factor = r.nextDouble() * (1 - .8) + .8;
			style.textSize = (int) (Style.defaultTextSize * factor);
			style.fallingSpeed = (int) (Style.defaultFallingSpeed * Math.pow(
					factor, 4));

			if (factor > .93) {
				style.maskFilter = regularFilter;
			} else if (factor <= .93 && factor >= .87) {
				style.maskFilter = slightBlurFilter;
			} else {
				style.maskFilter = blurFilter;
			}
		}
	}

	/**
	 * Configures any BitSequences parameters requiring the application context
	 *
	 * @param context
	 *            the application context
	 */
	public static void configure(Context context) {
		Style.initParameters(context);
	}

	/**
	 * Configures the BitSequence based on the display
	 *
	 * @param width
	 *            the width of the screen
	 * @param height
	 *            the height of the screen
	 */
	public static void setScreenDim(int width, int height) {
		HEIGHT = height;
	}

	public BitSequence(int x, long now) {
		for (int i = 0; i < Style.numBits; i++) {
			if (isRandom) {
				bits.add(getRandomBit(r));
			} else {
				bits.addFirst(getNextBit());
			}
		}
		this.x = x;
		reset(now);
	}

	/** Shifts the bits back by one and adds a new bit to the end */
	private void changeBit() {
		if (isRandom) {
			bits.removeFirst();
			bits.addLast(getRandomBit(r));
		}
	}

	private String getNextBit() {
		String s = Character.toString(charSet.charAt(curChar));
		curChar = (curChar + 1) % charSet.length();
		return s;
	}

	/**
	 * Gets a new random bit
	 *
	 * @param r
	 *            the {@link Random} object to use
	 * @return A new random bit as a {@link String}
	 */
	private String getRandomBit(Random r) {
		return symbols[r.nextInt(symbols.length)];
	}

	/**
	 * Gets the width the BitSequence would be on the screen
	 *
	 * @return the width of the BitSequence
	 */
	public static float getWidth() {
		measurePaint.setTextSize(Style.defaultTextSize);
		return measurePaint.measureText("0");
	}

	/**
	 * Draws this BitSequence on the screen
	 *
	 * @param canvas
	 *            the {@link Canvas} on which to draw the BitSequence
	 */
	public void draw(Canvas canvas) {
		Paint paint = style.paint;
		float bitY = y;
		paint.setAlpha(Style.alphaIncrement);
		for (int i = 0; i < bits.size(); i++) {
			canvas.drawText(bits.get(i), x, bitY, paint);
			bitY += style.textSize;
			paint.setAlpha(paint.getAlpha() + Style.alphaIncrement);
		}
	}
}
